const spec = {
  openapi: '3.0.3',
  info: {
    title: 'Aeon Reader API',
    description: 'Cloudflare Workers API for syncing bookmarks, reading progress, and reading history in Aeon Reader.',
    version: '1.0.0',
  },
  servers: [
    { url: 'https://aeon-reader-api.idoknow68.workers.dev', description: 'Production' },
    { url: 'http://localhost:8787', description: 'Local dev' },
  ],
  components: {
    securitySchemes: {
      BearerAuth: {
        type: 'http',
        scheme: 'bearer',
        bearerFormat: 'JWT',
      },
    },
    schemas: {
      ApiError: {
        type: 'object',
        properties: { error: { type: 'string' } },
        required: ['error'],
      },
      User: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          email: { type: 'string', format: 'email' },
          username: { type: 'string' },
        },
        required: ['id', 'email', 'username'],
      },
      AuthResponse: {
        type: 'object',
        properties: {
          token: { type: 'string', description: 'JWT token' },
          user: { '$ref': '#/components/schemas/User' },
        },
        required: ['token', 'user'],
      },
      Bookmark: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          user_id: { type: 'integer' },
          article_url: { type: 'string' },
          title: { type: 'string' },
          author: { type: 'string', nullable: true },
          hero_image_url: { type: 'string', nullable: true },
          bookmarked_at: { type: 'string', format: 'date-time' },
          synced_at: { type: 'string', format: 'date-time' },
        },
        required: ['id', 'user_id', 'article_url', 'title', 'bookmarked_at', 'synced_at'],
      },
      BookmarkPayload: {
        type: 'object',
        properties: {
          article_url: { type: 'string' },
          title: { type: 'string' },
          author: { type: 'string', nullable: true },
          hero_image_url: { type: 'string', nullable: true },
          bookmarked_at: { type: 'string', format: 'date-time' },
        },
        required: ['article_url', 'title', 'bookmarked_at'],
      },
      ReadingProgress: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          user_id: { type: 'integer' },
          article_url: { type: 'string' },
          last_block_index: { type: 'integer' },
          total_blocks: { type: 'integer' },
          saved_at: { type: 'string', format: 'date-time' },
          synced_at: { type: 'string', format: 'date-time' },
        },
        required: ['id', 'user_id', 'article_url', 'last_block_index', 'total_blocks', 'saved_at', 'synced_at'],
      },
      ProgressPayload: {
        type: 'object',
        properties: {
          article_url: { type: 'string' },
          last_block_index: { type: 'integer' },
          total_blocks: { type: 'integer' },
          saved_at: { type: 'string', format: 'date-time' },
        },
        required: ['article_url', 'last_block_index', 'total_blocks', 'saved_at'],
      },
      ReadingHistory: {
        type: 'object',
        properties: {
          id: { type: 'integer' },
          user_id: { type: 'integer' },
          article_url: { type: 'string' },
          title: { type: 'string' },
          author: { type: 'string', nullable: true },
          hero_image_url: { type: 'string', nullable: true },
          category: { type: 'string', nullable: true },
          read_at: { type: 'string', format: 'date-time' },
          synced_at: { type: 'string', format: 'date-time' },
        },
        required: ['id', 'user_id', 'article_url', 'title', 'read_at', 'synced_at'],
      },
      HistoryPayload: {
        type: 'object',
        properties: {
          article_url: { type: 'string' },
          title: { type: 'string' },
          author: { type: 'string', nullable: true },
          hero_image_url: { type: 'string', nullable: true },
          category: { type: 'string', nullable: true },
          read_at: { type: 'string', format: 'date-time' },
        },
        required: ['article_url', 'title', 'read_at'],
      },
      SyncResponse: {
        type: 'object',
        properties: {
          synced: { type: 'integer', description: 'Number of items synced' },
          skipped: { type: 'integer', description: 'Number of items skipped (always 0)' },
        },
        required: ['synced', 'skipped'],
      },
      StatsResponse: {
        type: 'object',
        properties: {
          total_bookmarks: { type: 'integer' },
          total_read: { type: 'integer' },
          total_progress_saved: { type: 'integer' },
        },
        required: ['total_bookmarks', 'total_read', 'total_progress_saved'],
      },
      BookmarkListResponse: {
        type: 'object',
        properties: {
          items: {
            type: 'array',
            items: { '$ref': '#/components/schemas/Bookmark' },
          },
        },
        required: ['items'],
      },
      ProgressListResponse: {
        type: 'object',
        properties: {
          items: {
            type: 'array',
            items: { '$ref': '#/components/schemas/ReadingProgress' },
          },
        },
        required: ['items'],
      },
      HistoryListResponse: {
        type: 'object',
        properties: {
          items: {
            type: 'array',
            items: { '$ref': '#/components/schemas/ReadingHistory' },
          },
        },
        required: ['items'],
      },
      DeleteResponse: {
        type: 'object',
        properties: {
          success: { type: 'boolean' },
        },
        required: ['success'],
      },
      SyncRequest: {
        type: 'object',
        properties: {
          items: {
            type: 'array',
            items: { type: 'object' },
            description: 'Array of items matching the endpoint type (BookmarkPayload, ProgressPayload, or HistoryPayload)',
          },
        },
        required: ['items'],
      },
    },
  },
  paths: {
    '/api/health': {
      get: {
        summary: 'Health check',
        tags: ['Health'],
        responses: {
          '200': {
            description: 'API is healthy',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: { status: { type: 'string', example: 'ok' } },
                },
              },
            },
          },
        },
      },
    },
    '/api/auth/register': {
      post: {
        summary: 'Register a new account',
        tags: ['Auth'],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: {
                  email: { type: 'string', format: 'email', example: 'user@example.com' },
                  username: { type: 'string', example: 'johndoe' },
                  password: { type: 'string', format: 'password', minLength: 8, example: 'password123' },
                },
                required: ['email', 'username', 'password'],
              },
            },
          },
        },
        responses: {
          '201': { description: 'Account created', content: { 'application/json': { schema: { '$ref': '#/components/schemas/AuthResponse' } } } },
          '400': { description: 'Missing fields or password too short', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
          '409': { description: 'Email already registered', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/auth/login': {
      post: {
        summary: 'Log in with email and password',
        tags: ['Auth'],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: {
                  email: { type: 'string', format: 'email', example: 'user@example.com' },
                  password: { type: 'string', format: 'password', example: 'password123' },
                },
                required: ['email', 'password'],
              },
            },
          },
        },
        responses: {
          '200': { description: 'Login successful', content: { 'application/json': { schema: { '$ref': '#/components/schemas/AuthResponse' } } } },
          '400': { description: 'Missing email or password', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
          '401': { description: 'Invalid credentials', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/auth/me': {
      get: {
        summary: 'Get current user info',
        tags: ['Auth'],
        security: [{ BearerAuth: [] }],
        responses: {
          '200': { description: 'User info', content: { 'application/json': { schema: { type: 'object', properties: { user: { '$ref': '#/components/schemas/User' } } } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/bookmarks': {
      get: {
        summary: 'Get all bookmarks',
        tags: ['Bookmarks'],
        security: [{ BearerAuth: [] }],
        responses: {
          '200': { description: 'List of bookmarks', content: { 'application/json': { schema: { '$ref': '#/components/schemas/BookmarkListResponse' } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/bookmarks/sync': {
      post: {
        summary: 'Sync bookmarks (upsert by article_url)',
        tags: ['Bookmarks'],
        security: [{ BearerAuth: [] }],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: {
                  items: {
                    type: 'array',
                    items: { '$ref': '#/components/schemas/BookmarkPayload' },
                  },
                },
                required: ['items'],
              },
            },
          },
        },
        responses: {
          '200': { description: 'Sync result', content: { 'application/json': { schema: { '$ref': '#/components/schemas/SyncResponse' } } } },
          '400': { description: 'Invalid request body', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/bookmarks/{url}': {
      delete: {
        summary: 'Delete a specific bookmark by article URL',
        tags: ['Bookmarks'],
        security: [{ BearerAuth: [] }],
        parameters: [
          {
            name: 'url',
            in: 'path',
            required: true,
            schema: { type: 'string' },
            description: 'The article URL (URL-encoded)',
          },
        ],
        responses: {
          '200': { description: 'Bookmark deleted', content: { 'application/json': { schema: { '$ref': '#/components/schemas/DeleteResponse' } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/progress': {
      get: {
        summary: 'Get all reading progress entries',
        tags: ['Reading Progress'],
        security: [{ BearerAuth: [] }],
        responses: {
          '200': { description: 'List of progress entries', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ProgressListResponse' } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/progress/sync': {
      post: {
        summary: 'Sync reading progress (upsert by article_url)',
        tags: ['Reading Progress'],
        security: [{ BearerAuth: [] }],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: {
                  items: {
                    type: 'array',
                    items: { '$ref': '#/components/schemas/ProgressPayload' },
                  },
                },
                required: ['items'],
              },
            },
          },
        },
        responses: {
          '200': { description: 'Sync result', content: { 'application/json': { schema: { '$ref': '#/components/schemas/SyncResponse' } } } },
          '400': { description: 'Invalid request body', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/history': {
      get: {
        summary: 'Get all reading history entries',
        tags: ['Reading History'],
        security: [{ BearerAuth: [] }],
        responses: {
          '200': { description: 'List of history entries', content: { 'application/json': { schema: { '$ref': '#/components/schemas/HistoryListResponse' } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/history/sync': {
      post: {
        summary: 'Sync reading history (upsert by article_url)',
        tags: ['Reading History'],
        security: [{ BearerAuth: [] }],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: {
                  items: {
                    type: 'array',
                    items: { '$ref': '#/components/schemas/HistoryPayload' },
                  },
                },
                required: ['items'],
              },
            },
          },
        },
        responses: {
          '200': { description: 'Sync result', content: { 'application/json': { schema: { '$ref': '#/components/schemas/SyncResponse' } } } },
          '400': { description: 'Invalid request body', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
    '/api/user/stats': {
      get: {
        summary: 'Get user stats (bookmarks count, articles read, progress saved)',
        tags: ['User'],
        security: [{ BearerAuth: [] }],
        responses: {
          '200': { description: 'User stats', content: { 'application/json': { schema: { '$ref': '#/components/schemas/StatsResponse' } } } },
          '401': { description: 'Unauthorized', content: { 'application/json': { schema: { '$ref': '#/components/schemas/ApiError' } } } },
        },
      },
    },
  },
}

export default spec
