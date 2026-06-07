import { Hono } from 'hono'
import { Bindings, JWTPayload, StatsResponse } from './types'
import { authMiddleware } from './middleware'

const stats = new Hono<{ Bindings: Bindings }>()

stats.use('*', authMiddleware)

stats.get('/', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const userId = payload.sub
  const db = c.env.DB

  const bookmarkCount = await db
    .prepare('SELECT COUNT(*) as count FROM bookmarks WHERE user_id = ?')
    .bind(userId)
    .first<{ count: number }>()

  const historyCount = await db
    .prepare('SELECT COUNT(*) as count FROM reading_history WHERE user_id = ?')
    .bind(userId)
    .first<{ count: number }>()

  const progressCount = await db
    .prepare('SELECT COUNT(*) as count FROM reading_progress WHERE user_id = ?')
    .bind(userId)
    .first<{ count: number }>()

  const response: StatsResponse = {
    total_bookmarks: bookmarkCount?.count ?? 0,
    total_read: historyCount?.count ?? 0,
    total_progress_saved: progressCount?.count ?? 0
  }

  return c.json(response)
})

export default stats
