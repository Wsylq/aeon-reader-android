import { Hono } from 'hono'
import { Bindings, JWTPayload, SyncPayload, SyncResponse } from './types'
import { createDB } from './db'
import { authMiddleware } from './middleware'

const progress = new Hono<{ Bindings: Bindings }>()

progress.use('*', authMiddleware)

progress.get('/', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const db = createDB(c.env)
  const items = await db.progress.getAll(payload.sub)
  return c.json({ items })
})

progress.post('/sync', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const body = await c.req.json<SyncPayload<{
    article_url: string
    last_block_index: number
    total_blocks: number
    saved_at: string
  }>>()

  if (!body.items || !Array.isArray(body.items)) {
    return c.json({ error: 'items array is required' }, 400)
  }

  const db = createDB(c.env)
  let synced = 0

  for (const item of body.items) {
    await db.progress.upsert(payload.sub, item)
    synced++
  }

  const response: SyncResponse = { synced, skipped: 0 }
  return c.json(response)
})

export default progress
