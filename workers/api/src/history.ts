import { Hono } from 'hono'
import { Bindings, JWTPayload, SyncPayload, SyncResponse } from './types'
import { createDB } from './db'
import { authMiddleware } from './middleware'

const history = new Hono<{ Bindings: Bindings }>()

history.use('*', authMiddleware)

history.get('/', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const db = createDB(c.env)
  const items = await db.history.getAll(payload.sub)
  return c.json({ items })
})

history.post('/sync', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const body = await c.req.json<SyncPayload<{
    article_url: string
    title: string
    author: string | null
    hero_image_url: string | null
    category: string | null
    read_at: string
  }>>()

  if (!body.items || !Array.isArray(body.items)) {
    return c.json({ error: 'items array is required' }, 400)
  }

  const db = createDB(c.env)
  let synced = 0

  for (const item of body.items) {
    await db.history.upsert(payload.sub, item)
    synced++
  }

  const response: SyncResponse = { synced, skipped: 0 }
  return c.json(response)
})

export default history
