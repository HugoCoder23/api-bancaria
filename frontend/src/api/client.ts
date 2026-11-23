import type { ApiErrorBody } from './types';

const BASE = import.meta.env.VITE_API_URL ?? '';
const TOKEN_KEY = 'banca.token';

export class ApiError extends Error {
  status: number;
  code?: string;
  reference?: string;
  fieldErrors: Record<string, string>;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.status = body.status;
    this.code = body.code;
    this.reference = body.reference;
    this.fieldErrors = Object.fromEntries((body.fieldErrors ?? []).map((f) => [f.field, f.message]));
  }
}

export const tokenStore = {
  get(): string | null {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  },
  set(token: string | null) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch { /* almacenamiento no disponible */ }
  },
};

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export type Query = Record<string, string | number | boolean | null | undefined>;

export function toQuery(params: Query = {}): string {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  });
  const s = qs.toString();
  return s ? `?${s}` : '';
}

/** Clave nueva para una operación de dinero. Se reutiliza en los reintentos de esa misma operación. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

interface RawResult<T> { data: T; headers: Headers }

async function request<T>(method: string, path: string, body?: unknown, extra: Record<string, string> = {}): Promise<RawResult<T>> {
  const headers: Record<string, string> = { ...extra };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError({ status: 0, code: 'NETWORK', message: 'No se pudo conectar con el servidor' });
  }

  if (res.status === 401 && !path.startsWith('/api/auth/login')) onUnauthorized();
  if (!res.ok) {
    let err: ApiErrorBody;
    try {
      err = await res.json();
    } catch {
      err = {
        status: res.status,
        message: res.status >= 500
          ? 'El servidor no está disponible. Intenta de nuevo en unos segundos'
          : `Error ${res.status}`,
      };
    }
    throw new ApiError({ ...err, status: res.status });
  }
  const data = res.status === 204 ? (undefined as T) : ((await res.json()) as T);
  return { data, headers: res.headers };
}

export const api = {
  get: async <T>(path: string, params?: Query) => (await request<T>('GET', path + toQuery(params))).data,
  post: async <T>(path: string, body?: unknown) => (await request<T>('POST', path, body)).data,
  patch: async <T>(path: string, body?: unknown) => (await request<T>('PATCH', path, body)).data,

  /** POST de una operación de dinero con cabecera Idempotency-Key. */
  async money<T>(path: string, body: unknown, idempotencyKey: string): Promise<{ data: T; replayed: boolean }> {
    const r = await request<T>('POST', path, body, { 'Idempotency-Key': idempotencyKey });
    return { data: r.data, replayed: r.headers.get('Idempotent-Replayed') === 'true' };
  },
};
