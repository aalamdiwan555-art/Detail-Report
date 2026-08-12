import {
  createHash,
  createHmac,
  randomBytes,
  scrypt as nodeScrypt,
  timingSafeEqual,
} from "node:crypto";
import { promisify } from "node:util";
import type { NextFunction, Request, Response } from "express";
import { and, eq, gt } from "drizzle-orm";
import { db } from "@workspace/db";
import { sessionsTable, usersTable, type User } from "@workspace/db/schema";

const scrypt = promisify(nodeScrypt);
const SESSION_COOKIE = "ultra_session";
const SESSION_TTL_MS = 1000 * 60 * 60 * 24 * 30;

function sessionSecret(): string {
  const value = process.env.SESSION_SECRET;
  if (!value) {
    throw new Error("SESSION_SECRET must be configured before starting the API.");
  }
  return value;
}

export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16).toString("hex");
  const derived = (await scrypt(password, salt, 64)) as Buffer;
  return `${salt}:${derived.toString("hex")}`;
}

export async function verifyPassword(password: string, encoded: string): Promise<boolean> {
  const [salt, expectedHex] = encoded.split(":");
  if (!salt || !expectedHex) return false;
  const actual = (await scrypt(password, salt, 64)) as Buffer;
  const expected = Buffer.from(expectedHex, "hex");
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

export function hashToken(token: string): string {
  return createHmac("sha256", sessionSecret()).update(token).digest("hex");
}

export function publicUser(user: User) {
  const isExpired =
    user.status === "active" &&
    user.expirationAt !== null &&
    user.expirationAt.getTime() <= Date.now();

  return {
    id: user.id,
    email: user.email,
    role: user.role,
    status: isExpired ? "expired" : user.status,
    expirationAt: user.expirationAt?.toISOString() ?? null,
    createdAt: user.createdAt.toISOString(),
  };
}

export async function createSession(userId: string, response: Response): Promise<void> {
  const token = randomBytes(32).toString("base64url");
  const expiresAt = new Date(Date.now() + SESSION_TTL_MS);
  await db.insert(sessionsTable).values({
    userId,
    tokenHash: hashToken(token),
    expiresAt,
  });
  response.cookie(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    expires: expiresAt,
    path: "/",
  });
  response.setHeader("x-session-token", token);
}

function readSessionToken(request: Request): string | null {
  const cookie = request.cookies?.[SESSION_COOKIE];
  if (typeof cookie === "string" && cookie.length > 0) return cookie;
  const authorization = request.header("authorization");
  if (authorization?.startsWith("Bearer ")) return authorization.slice(7).trim();
  return null;
}

export async function findSessionUser(request: Request): Promise<User | null> {
  const token = readSessionToken(request);
  if (!token) return null;
  const rows = await db
    .select({ user: usersTable })
    .from(sessionsTable)
    .innerJoin(usersTable, eq(sessionsTable.userId, usersTable.id))
    .where(and(eq(sessionsTable.tokenHash, hashToken(token)), gt(sessionsTable.expiresAt, new Date())))
    .limit(1);
  return rows[0]?.user ?? null;
}

export async function requireAuth(
  request: Request,
  response: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const user = await findSessionUser(request);
    if (!user) {
      response.status(401).json({ message: "Authentication required." });
      return;
    }
    request.user = user;
    next();
  } catch (error) {
    next(error);
  }
}

export function requireAdmin(
  request: Request,
  response: Response,
  next: NextFunction,
): void {
  if (request.user?.role !== "admin") {
    response.status(403).json({ message: "Administrator access required." });
    return;
  }
  next();
}

export async function deleteCurrentSession(request: Request): Promise<void> {
  const token = readSessionToken(request);
  if (token) {
    await db.delete(sessionsTable).where(eq(sessionsTable.tokenHash, hashToken(token)));
  }
}

export function passwordResetDigest(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}