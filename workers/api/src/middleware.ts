import { jwt } from 'hono/jwt'
import { createMiddleware } from 'hono/factory'
import { Bindings } from './types'

export const authMiddleware = createMiddleware<{ Bindings: Bindings }>(async (c, next) => {
  const jwtMiddleware = jwt({ secret: c.env.JWT_SECRET })
  return jwtMiddleware(c, next)
})
