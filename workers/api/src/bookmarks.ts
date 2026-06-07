import { Hono } from 'hono'
import { Bindings, JWTPayload, SyncPayload, SyncResponse } from './types'
import { createDB } from './db'
import { authMiddleware } from './middleware'

const bookmarks = new Hono<{ Bindings: Bindings }>()

bookmarks.use('*', authMiddleware)

bookmarks.get('/', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const db = createDB(c.env)
  const items = await db.bookmarks.getAll(payload.sub)
  return c.json({ items })
})

bookmarks.post('/sync', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const body = await c.req.json<SyncPayload<{
    article_url: string
    title: string
    author: string | null
    hero_image_url: string | null
    bookmarked_at: string
  }>>()

  if (!body.items || !Array.isArray(body.items)) {
    return c.json({ error: 'items array is required' }, 400)
  }

  const db = createDB(c.env)
  let synced = 0

  for (const item of body.items) {
    await db.bookmarks.upsert(payload.sub, item)
    synced++
  }

  const response: SyncResponse = { synced, skipped: 0 }
  return c.json(response)
})

bookmarks.delete('/:url', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const url = decodeURIComponent(c.req.param('url'))
  const db = createDB(c.env)
  await db.bookmarks.remove(payload.sub, url)
  return c.json({ success: true })
})

export default bookmarks
