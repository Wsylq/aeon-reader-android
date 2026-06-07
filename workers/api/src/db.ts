import { Bindings, User, Bookmark, ReadingProgress, ReadingHistory } from './types'

function users(db: D1Database) {
  return {
    async create(email: string, username: string, passwordHash: string): Promise<User | null> {
      await db
        .prepare('INSERT INTO users (email, username, password_hash) VALUES (?, ?, ?)')
        .bind(email, username, passwordHash)
        .run()
      return db
        .prepare('SELECT id, email, username, created_at FROM users WHERE email = ?')
        .bind(email)
        .first<User | null>()
    },

    async findByEmail(email: string): Promise<(User & { password_hash: string }) | null> {
      return db
        .prepare('SELECT id, email, username, password_hash, created_at FROM users WHERE email = ?')
        .bind(email)
        .first<(User & { password_hash: string }) | null>()
    },

    async findById(id: number): Promise<User | null> {
      return db
        .prepare('SELECT id, email, username, created_at FROM users WHERE id = ?')
        .bind(id)
        .first<User | null>()
    }
  }
}

function bookmarks(db: D1Database) {
  return {
    async getAll(userId: number): Promise<Bookmark[]> {
      const result = await db
        .prepare('SELECT * FROM bookmarks WHERE user_id = ? ORDER BY bookmarked_at DESC')
        .bind(userId)
        .all<Bookmark>()
      return result.results
    },

    async upsert(userId: number, item: Omit<Bookmark, 'id' | 'user_id' | 'synced_at'>): Promise<void> {
      await db
        .prepare(`
          INSERT INTO bookmarks (user_id, article_url, title, author, hero_image_url, bookmarked_at)
          VALUES (?, ?, ?, ?, ?, ?)
          ON CONFLICT(user_id, article_url) DO UPDATE SET
            title = excluded.title,
            author = excluded.author,
            hero_image_url = excluded.hero_image_url,
            synced_at = datetime('now')
        `)
        .bind(userId, item.article_url, item.title, item.author, item.hero_image_url, item.bookmarked_at)
        .run()
    },

    async remove(userId: number, articleUrl: string): Promise<void> {
      await db
        .prepare('DELETE FROM bookmarks WHERE user_id = ? AND article_url = ?')
        .bind(userId, articleUrl)
        .run()
    }
  }
}

function progress(db: D1Database) {
  return {
    async getAll(userId: number): Promise<ReadingProgress[]> {
      const result = await db
        .prepare('SELECT * FROM reading_progress WHERE user_id = ? ORDER BY saved_at DESC')
        .bind(userId)
        .all<ReadingProgress>()
      return result.results
    },

    async upsert(userId: number, item: Omit<ReadingProgress, 'id' | 'user_id' | 'synced_at'>): Promise<void> {
      await db
        .prepare(`
          INSERT INTO reading_progress (user_id, article_url, last_block_index, total_blocks, saved_at)
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT(user_id, article_url) DO UPDATE SET
            last_block_index = excluded.last_block_index,
            total_blocks = excluded.total_blocks,
            saved_at = excluded.saved_at,
            synced_at = datetime('now')
        `)
        .bind(userId, item.article_url, item.last_block_index, item.total_blocks, item.saved_at)
        .run()
    }
  }
}

function history(db: D1Database) {
  return {
    async getAll(userId: number): Promise<ReadingHistory[]> {
      const result = await db
        .prepare('SELECT * FROM reading_history WHERE user_id = ? ORDER BY read_at DESC')
        .bind(userId)
        .all<ReadingHistory>()
      return result.results
    },

    async upsert(userId: number, item: Omit<ReadingHistory, 'id' | 'user_id' | 'synced_at'>): Promise<void> {
      await db
        .prepare(`
          INSERT INTO reading_history (user_id, article_url, title, author, hero_image_url, category, read_at)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT(user_id, article_url) DO UPDATE SET
            title = excluded.title,
            author = excluded.author,
            hero_image_url = excluded.hero_image_url,
            category = excluded.category,
            read_at = excluded.read_at,
            synced_at = datetime('now')
        `)
        .bind(userId, item.article_url, item.title, item.author, item.hero_image_url, item.category, item.read_at)
        .run()
    }
  }
}

export function createDB(env: Bindings) {
  return {
    users: users(env.DB),
    bookmarks: bookmarks(env.DB),
    progress: progress(env.DB),
    history: history(env.DB)
  }
}