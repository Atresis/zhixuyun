import type { AuthError, AuthUser, Role } from "./auth.types";

export const TOKEN_KEY = "zhixuyun_access_token";
export const USER_KEY = "zhixuyun_current_user";

export function homeForRole(role: Role): string {
  return `/${role.toLowerCase()}/dashboard`;
}

export function isRole(value: unknown): value is Role {
  return value === "STUDENT" || value === "TEACHER" || value === "ADMIN";
}

export function readToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY) || "";
}

export function saveToken(token: string, remember: boolean): void {
  clearToken();
  (remember ? localStorage : sessionStorage).setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(TOKEN_KEY);
}

export function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    const user = JSON.parse(raw) as AuthUser;
    return isRole(user.role) ? user : null;
  } catch {
    return null;
  }
}

export function saveStoredUser(user: AuthUser, remember: boolean): void {
  sessionStorage.removeItem(USER_KEY);
  localStorage.removeItem(USER_KEY);
  (remember ? localStorage : sessionStorage).setItem(USER_KEY, JSON.stringify(user));
}

export function clearStoredUser(): void {
  sessionStorage.removeItem(USER_KEY);
  localStorage.removeItem(USER_KEY);
}

export function normalizeAuthError(error: unknown): AuthError {
  const source = error as Partial<AuthError> & { code?: string; status?: number };
  const normalized = new Error(source.message || "登录失败") as AuthError;
  normalized.status = source.status;
  normalized.code = resolveErrorCode(source.code, source.status);
  return normalized;
}

function resolveErrorCode(code: string | undefined, status: number | undefined): AuthError["code"] {
  if (code === "ACCOUNT_DISABLED" || status === 403) return "ACCOUNT_DISABLED";
  if (code === "INVALID_CREDENTIALS" || status === 401) return "INVALID_CREDENTIALS";
  if (code === "SESSION_EXPIRED") return "SESSION_EXPIRED";
  if (code === "NETWORK_ERROR") return "NETWORK_ERROR";
  return "UNKNOWN";
}
