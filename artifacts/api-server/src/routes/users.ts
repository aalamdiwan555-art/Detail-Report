import { Router, type IRouter } from "express";
import { desc, eq } from "drizzle-orm";
import { z } from "zod/v4";
import { db } from "@workspace/db";
import { auditLogsTable, usersTable } from "@workspace/db/schema";
import { publicUser, requireAdmin, requireAuth } from "../lib/auth";

const router: IRouter = Router();
router.use(requireAuth, requireAdmin);

router.get("/users", async (_request, response, next) => {
  try {
    const users = await db.select().from(usersTable).orderBy(desc(usersTable.createdAt));
    response.json({ users: users.map(publicUser) });
  } catch (error) {
    next(error);
  }
});

router.patch("/users/:id/license", async (request, response, next) => {
  try {
    const parsed = z.object({ days: z.number().int().positive().nullable() }).safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ message: "days must be a positive integer or null for lifetime." });
      return;
    }
    const [target] = await db.select().from(usersTable).where(eq(usersTable.id, request.params.id)).limit(1);
    if (!target) {
      response.status(404).json({ message: "User not found." });
      return;
    }
    const now = Date.now();
    const current = target.expirationAt?.getTime() ?? 0;
    const expirationAt =
      parsed.data.days === null
        ? null
        : new Date(Math.max(now, current) + parsed.data.days * 24 * 60 * 60 * 1000);
    const [updated] = await db
      .update(usersTable)
      .set({
        status: "active",
        expirationAt,
        updatedAt: new Date(),
      })
      .where(eq(usersTable.id, target.id))
      .returning();
    await db.insert(auditLogsTable).values({
      adminId: request.user!.id,
      action: "grant_license",
      targetUserId: target.id,
      metadata: { days: parsed.data.days },
    });
    response.json({ user: publicUser(updated) });
  } catch (error) {
    next(error);
  }
});

router.post("/users/:id/reject", async (request, response, next) => {
  try {
    const [updated] = await db
      .update(usersTable)
      .set({ status: "rejected", expirationAt: null, updatedAt: new Date() })
      .where(eq(usersTable.id, request.params.id))
      .returning();
    if (!updated) {
      response.status(404).json({ message: "User not found." });
      return;
    }
    await db.insert(auditLogsTable).values({
      adminId: request.user!.id,
      action: "reject_user",
      targetUserId: updated.id,
      metadata: {},
    });
    response.json({ user: publicUser(updated) });
  } catch (error) {
    next(error);
  }
});

router.get("/audit-logs", async (_request, response, next) => {
  try {
    const logs = await db.select().from(auditLogsTable).orderBy(desc(auditLogsTable.createdAt)).limit(100);
    response.json({ logs });
  } catch (error) {
    next(error);
  }
});

export default router;