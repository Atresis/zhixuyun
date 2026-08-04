import { apiRequest, jsonBody } from "./client";
import type { AuthUser, LoginResponse } from "../features/auth/auth.types";

export const authApi = {
  login: (account: string, password: string) =>
    apiRequest<LoginResponse>("/auth/login", { method: "POST", ...jsonBody({ loginName: account, account, password }) }),
  me: () => apiRequest<AuthUser>("/auth/me"),
  logout: () => apiRequest<void>("/auth/logout", { method: "POST" }),
  changePassword: (oldPassword: string, newPassword: string) =>
    apiRequest<void>("/auth/change-password", { method: "POST", ...jsonBody({ oldPassword, newPassword }) }),
};
