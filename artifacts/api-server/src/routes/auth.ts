import { Router, type IRouter } from "express";
import { and, eq, gt, isNull } from "drizzle-orm";
import { z } from "zod/v4";
import { db } from "@workspace/db";
import {
  passwordResetTokensTable,
  sessionsTable,
  usersTable,
} from "@workspace/db/schema";
import {
  createSession,
  deleteCurrentSession,
  findSessionUser,
  hashPassword,
  passwordResetDigest,
  publicUser,
  requireAuth,
  verifyPassword,
} from "../lib/auth";
import { randomBytes } from "node:crypto";

const router: IRouter = Router();
const credentialsSchema = z.object({
  email: z.email().transform((value) => value.trim().toLowerCase()),
  password: z.string().min(8).max(128),
});

function validationError(error: unknown): string {
  return error instanceof z.ZodError
    ? error.issues.map((issue) => issue.message).join(" ")
    : "Invalid request.";
}

router.post("/auth/register", async (request, response, next) => {
  try {
    const parsed = credentialsSchema.safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ message: validationError(parsed.error) });
      return;
    }
    const existing = await db
      .select({ id: usersTable.id })
      .from(usersTable)
      .where(eq(usersTable.email, parsed.data.email))
      .limit(1);
    if (existing.length > 0) {
      response.status(409).json({ message: "An account with this email already exists." });
      return;
    }
    const [user] = await db
      .insert(usersTable)
      .values({
        email: parsed.data.email,
        passwordHash: await hashPassword(parsed.data.password),
      })
      .returning();
    await createSession(user.id, response);
    response.status(201).json({ user: publicUser(user) });
  } catch (error) {
    next(error);
  }
});

router.post("/auth/login", async (request, response, next) => {
  try {
    const parsed = credentialsSchema.safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ message: validationError(parsed.error) });
      return;
    }
    const [user] = await db
      .select()
      .from(usersTable)
      .where(eq(usersTable.email, parsed.data.email))
      .limit(1);
    if (!user || !(await verifyPassword(parsed.data.password, user.passwordHash))) {
      response.status(401).json({ message: "Incorrect email or password." });
      return;
    }
    await createSession(user.id, response);
    response.json({ user: publicUser(user) });
  } catch (error) {
    next(error);
  }
});

router.post("/auth/logout", requireAuth, async (request, response, next) => {
  try {
    await deleteCurrentSession(request);
    response.clearCookie("ultra_session", { httpOnly: true, sameSite: "lax", path: "/" });
    response.status(204).send();
  } catch (error) {
    next(error);
  }
});

router.get("/auth/me", requireAuth, (request, response) => {
  response.json({ user: publicUser(request.user!) });
});

router.post("/auth/change-password", requireAuth, async (request, response, next) => {
  try {
    const schema = z.object({
      currentPassword: z.string().min(1),
      newPassword: z.string().min(8).max(128),
    });
    const parsed = schema.safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ message: validationError(parsed.error) });
      return;
    }
    if (!(await verifyPassword(parsed.data.currentPassword, request.user!.passwordHash))) {
      response.status(400).json({ message: "Current password is incorrect." });
      return;
    }
    await db
      .update(usersTable)
      .set({ passwordHash: await hashPassword(parsed.data.newPassword), updatedAt: new Date() })
      .where(eq(usersTable.id, request.user!.id));
    await db.delete(sessionsTable).where(eq(sessionsTable.userId, request.user!.id));
    response.clearCookie("ultra_session", { httpOnly: true, sameSite: "lax", path: "/" });
    response.status(204).send();
  } catch (error) {
    next(error);
  }
});

router.post("/auth/password-reset/request", async (request, response, next) => {
  try {
    const parsed = z.object({ email: z.email() }).safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ message: "Enter a valid email address." });
      return;
    }
    const [user] = await db
      .select({ id: usersTable.id })
      .from(usersTable)
      .where(eq(usersTable.email, parsed.data.email.trim().toLowerCase()))
      .limit(1);
    if (user) {
      const token = randomBytes(32).toString("base64url");
      await db.insert(passwordResetTokensTable).values({
        userId: user.id,
        tokenHash: passwordResetDigest(token),
        expiresAt: new Date(Date.now() + 1000 * 60 * 30),
      });
      request.log.info({ userId: user.id }, "Password reset requested");
      if (process.env.NODE_ENV !== "production") {
        response.setHeader("x-development-reset-token", token);
      }
    }
    response.json({ message: "If that email exists, reset instructions have been created." });
  } catch (error) {
    next(error);
  }
});

router.post("/auth/password-reset/complete", async (request, response, next) => {
  try {
    const parsed = z
      .object({ token: z.string().min(20), newPassword: z.string().min(8).max(128) })
      .safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ message: validationError(parsed.error) });
      return;
    }
    const [reset] = await db
      .select()
      .from(passwordResetTokensTable)
      .where(
        and(
          eq(passwordResetTokensTable.tokenHash, passwordResetDigest(parsed.data.token)),
          isNull(passwordResetTokensTable.usedAt),
          gt(passwordResetTokensTable.expiresAt, new Date()),
        ),
      )
      .limit(1);
    if (!reset) {
      response.status(400).json({ message: "This reset link is invalid or expired." });
      return;
    }
    await db
      .update(usersTable)
      .set({ passwordHash: await hashPassword(parsed.data.newPassword), updatedAt: new Date() })
      .where(eq(usersTable.id, reset.userId));
    await db
      .update(passwordResetTokensTable)
      .set({ usedAt: new Date() })
      .where(eq(passwordResetTokensTable.id, reset.id));
    await db.delete(sessionsTable).where(eq(sessionsTable.userId, reset.userId));
    response.status(204).send();
  } catch (error) {
    next(error);
  }
});

export default router;