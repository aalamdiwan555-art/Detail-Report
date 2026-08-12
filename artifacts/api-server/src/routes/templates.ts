import { Router, type IRouter } from "express";
import { desc, eq } from "drizzle-orm";
import { z } from "zod/v4";
import { db } from "@workspace/db";
import { templatesTable } from "@workspace/db/schema";
import { requireAdmin, requireAuth } from "../lib/auth";

const router: IRouter = Router();

router.get("/templates", requireAuth, async (_request, response, next) => {
  try {
    const templates = await db
      .select({
        id: templatesTable.id,
        name: templatesTable.name,
        description: templatesTable.description,
        downloadUrl: templatesTable.downloadUrl,
        imageData: templatesTable.imageData,
        confidence: templatesTable.confidence,
        isActive: templatesTable.isActive,
      })
      .from(templatesTable)
      .where(eq(templatesTable.isActive, true))
      .orderBy(desc(templatesTable.updatedAt));
    response.json({ templates });
  } catch (error) {
    next(error);
  }
});

router.post("/templates", requireAuth, requireAdmin, async (request, response, next) => {
  try {
    const parsed = z
      .object({
        name: z.string().trim().min(1).max(120),
        description: z.string().trim().max(500).default(""),
        confidence: z.number().min(0).max(1).default(0.8),
        imageData: z.string().max(14_000_000).nullable().optional(),
      })
      .safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ message: "Invalid template. Check its name, confidence, and image size." });
      return;
    }
    const [template] = await db
      .insert(templatesTable)
      .values({
        name: parsed.data.name,
        description: parsed.data.description,
        confidence: parsed.data.confidence,
        imageData: parsed.data.imageData ?? null,
        createdBy: request.user!.id,
      })
      .returning();
    response.status(201).json({ template });
  } catch (error) {
    next(error);
  }
});

router.patch("/templates/:id", requireAuth, requireAdmin, async (request, response, next) => {
  try {
    const parsed = z.object({ isActive: z.boolean() }).safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ message: "isActive must be a boolean." });
      return;
    }
    const [template] = await db
      .update(templatesTable)
      .set({ isActive: parsed.data.isActive, updatedAt: new Date() })
      .where(eq(templatesTable.id, String(request.params.id)))
      .returning();
    if (!template) {
      response.status(404).json({ message: "Template not found." });
      return;
    }
    response.json({ template });
  } catch (error) {
    next(error);
  }
});

router.delete("/templates/:id", requireAuth, requireAdmin, async (request, response, next) => {
  try {
    const [template] = await db
      .delete(templatesTable)
      .where(eq(templatesTable.id, String(request.params.id)))
      .returning({ id: templatesTable.id });
    if (!template) {
      response.status(404).json({ message: "Template not found." });
      return;
    }
    response.status(204).send();
  } catch (error) {
    next(error);
  }
});

export default router;