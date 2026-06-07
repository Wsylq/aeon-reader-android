import { Hono } from 'hono'
import { sign } from 'hono/jwt'
import { Bindings, JWTPayload } from './types'
import { createDB } from './db'
import { authMiddleware } from './middleware'

async function hashPassword(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), { name: 'PBKDF2' }, false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt, iterations: 100000, hash: 'SHA-512' }, key, 512)
  const toHex = (a: Uint8Array) => Array.from(a).map(b => b.toString(16).padStart(2, '0')).join('')
  return toHex(salt) + ':' + toHex(new Uint8Array(bits))
}

async function verifyPassword(password: string, stored: string): Promise<boolean> {
  const [saltHex, hashHex] = stored.split(':')
  const salt = new Uint8Array(saltHex.match(/.{2}/g)!.map(b => parseInt(b, 16)))
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), { name: 'PBKDF2' }, false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt, iterations: 100000, hash: 'SHA-512' }, key, 512)
  const computed = Array.from(new Uint8Array(bits)).map(b => b.toString(16).padStart(2, '0')).join('')
  return computed === hashHex
}

const auth = new Hono<{ Bindings: Bindings }>()

auth.post('/register', async (c) => {
  const { email, username, password } = await c.req.json()
  if (!email || !username || !password) return c.json({ error: 'all fields required' }, 400)
  if (password.length < 8) return c.json({ error: 'password must be at least 8 characters' }, 400)

  const db = createDB(c.env)
  const existing = await db.users.findByEmail(email)
  if (existing) return c.json({ error: 'email already registered' }, 409)

  const passwordHash = await hashPassword(password)
  const user = await db.users.create(email, username, passwordHash)
  if (!user) return c.json({ error: 'failed to create user' }, 500)

  const payload: JWTPayload = { sub: user.id, email: user.email, username: user.username }
  const token = await sign(payload, c.env.JWT_SECRET)
  return c.json({ token, user: { id: user.id, email: user.email, username: user.username } }, 201)
})

auth.post('/login', async (c) => {
  const { email, password } = await c.req.json()
  if (!email || !password) return c.json({ error: 'email and password required' }, 400)

  const db = createDB(c.env)
  const user = await db.users.findByEmail(email)
  if (!user) return c.json({ error: 'invalid credentials' }, 401)

  const valid = await verifyPassword(password, user.password_hash)
  if (!valid) return c.json({ error: 'invalid credentials' }, 401)

  const payload: JWTPayload = { sub: user.id, email: user.email, username: user.username }
  const token = await sign(payload, c.env.JWT_SECRET)
  return c.json({ token, user: { id: user.id, email: user.email, username: user.username } })
})

auth.get('/me', authMiddleware, async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const db = createDB(c.env)
  const user = await db.users.findById(payload.sub)
  if (!user) return c.json({ error: 'user not found' }, 404)
  return c.json({ user: { id: user.id, email: user.email, username: user.username } })
})

export default auth