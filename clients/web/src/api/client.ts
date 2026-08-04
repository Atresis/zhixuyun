import { readToken } from "../features/auth/auth.utils";

export type ApiError = Error & { status?: number; code?: string };

let unauthorizedHandler: (() => void) | undefined;

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = readToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");

  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, { ...options, headers });
  } catch {
    const error = new Error("网络连接失败，请检查服务是否已启动") as ApiError;
    error.code = "NETWORK_ERROR";
    throw error;
  }

  if (response.status === 401 && unauthorizedHandler) unauthorizedHandler();
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string; code?: string };
    const error = new Error(body.message || `请求失败（${response.status}）`) as ApiError;
    error.status = response.status;
    error.code = body.code;
    throw error;
  }
  return response.status === 204 ? ({} as T) : response.json() as Promise<T>;
}

export async function apiDownload(path: string): Promise<{ blob: Blob; filename: string }> {
  const headers = new Headers();
  const token = readToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`/api/v1${path}`, { headers });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message || `下载失败（${response.status}）`);
  }
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const quoted = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  return { blob: await response.blob(), filename: decodeURIComponent(encoded || quoted || "download") };
}

export function jsonBody(value: unknown): Pick<RequestInit, "body"> {
  return { body: JSON.stringify(value) };
}
