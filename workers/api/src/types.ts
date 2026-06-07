export interface User {
  id: number
  email: string
  username: string
  created_at: string
}

export interface Bookmark {
  id: number
  user_id: number
  article_url: string
  title: string
  author: string | null
  hero_image_url: string | null
  bookmarked_at: string
  synced_at: string
}

export interface ReadingProgress {
  id: number
  user_id: number
  article_url: string
  last_block_index: number
  total_blocks: number
  saved_at: string
  synced_at: string
}

export interface ReadingHistory {
  id: number
  user_id: number
  article_url: string
  title: string
  author: string | null
  hero_image_url: string | null
  category: string | null
  read_at: string
  synced_at: string
}

export interface SyncPayload<T> {
  items: T[]
}

export interface SyncResponse {
  synced: number
  skipped: number
}

export interface StatsResponse {
  total_bookmarks: number
  total_read: number
  total_progress_saved: number
}

export interface ApiError {
  error: string
}

export interface JWTPayload {
  sub: number
  email: string
  username: string
}

export type Bindings = {
  DB: D1Database
  JWT_SECRET: string
}
