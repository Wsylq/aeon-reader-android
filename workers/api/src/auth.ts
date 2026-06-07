import { Hono } from 'hono'
import { sign } from 'hono/jwt'
import { Bindings, JWTPayload } from './types'
import { createDB } from './db'

const auth = new Hono<{ Bindings: Bindings }>()

auth.post('/register', async (c) => {
  const { email, username, password } = await c.req.json<{
    email: string
    username: string
    password: string
  }>()

  if (!email || !username || !password) {
    return c.json({ error: 'email, username, and password are required' }, 400)
  }

  if (password.length < 8) {
    return c.json({ error: 'password must be at least 8 characters' }, 400)
  }

  const db = createDB(c.env)

  const existing = await db.users.findByEmail(email)
  if (existing) {
    return c.json({ error: 'email already registered' }, 409)
  }

  const bcrypt = await import('bcryptjs')
  const passwordHash = await bcrypt.hash(password, 10)
  const user = await db.users.create(email, username, passwordHash)

  const payload: JWTPayload = { sub: user.id, email: user.email, username: user.username }
  const token = await sign(payload, c.env.JWT_SECRET)

  return c.json({ token, user: { id: user.id, email: user.email, username: user.username } }, 201)
})

auth.post('/login', async (c) => {
  const { email, password } = await c.req.json<{ email: string; password: string }>()

  if (!email || !password) {
    return c.json({ error: 'email and password are required' }, 400)
  }

  const db = createDB(c.env)

  const user = await db.users.findByEmail(email)
  if (!user) {
    return c.json({ error: 'invalid email or password' }, 401)
  }

  const bcrypt = await import('bcryptjs')
  const valid = await bcrypt.compare(password, user.password_hash)
  if (!valid) {
    return c.json({ error: 'invalid email or password' }, 401)
  }

  const payload: JWTPayload = { sub: user.id, email: user.email, username: user.username }
  const token = await sign(payload, c.env.JWT_SECRET)

  return c.json({ token, user: { id: user.id, email: user.email, username: user.username } })
})

auth.get('/me', async (c) => {
  const payload = c.get('jwtPayload') as JWTPayload
  const db = createDB(c.env)
  const user = await db.users.findById(payload.sub)
  if (!user) {
    return c.json({ error: 'user not found' }, 404)
  }
  return c.json({ user: { id: user.id, email: user.email, username: user.username } })
})

export default auth
