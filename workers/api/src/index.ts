import { Hono } from 'hono'
import { cors } from 'hono/cors'
import { swaggerUI } from '@hono/swagger-ui'
import { Bindings } from './types'
import auth from './auth'
import bookmarks from './bookmarks'
import progress from './progress'
import history from './history'
import stats from './stats'
import openapiSpec from './openapi'

const app = new Hono<{ Bindings: Bindings }>()

app.use('/*', cors())

app.onError((err, c) => {
  return c.json({ error: err.message }, 500)
})

app.route('/api/auth', auth)
app.route('/api/bookmarks', bookmarks)
app.route('/api/progress', progress)
app.route('/api/history', history)
app.route('/api/user/stats', stats)

app.get('/api/health', (c) => c.json({ status: 'ok' }))

app.get('/api/openapi.json', (c) => c.json(openapiSpec))
app.get('/api/docs', swaggerUI({ url: '/api/openapi.json' }))

export default app