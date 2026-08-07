import { apiDownload, apiRequest, jsonBody } from "./client";
import type { AuthUser, LoginResponse, UserProfile } from "../features/auth/auth.types";

export const authApi = {
  login: (account: string, password: string) =>
    apiRequest<LoginResponse>("/auth/login", { method: "POST", ...jsonBody({ loginName: account, account, password }) }),
  me: () => apiRequest<AuthUser>("/auth/me"),
  profile: () => apiRequest<UserProfile>("/auth/profile"),
  updateProfile: (body: Pick<UserProfile, "displayName" | "email" | "phone" | "bio">) =>
    apiRequest<UserProfile>("/auth/profile", { method: "PATCH", ...jsonBody(body) }),
  uploadAvatar: (file: File) => {
    const body = new FormData();
    body.append("file", file);
    return apiRequest<UserProfile>("/auth/profile/avatar", { method: "POST", body });
  },
  deleteAvatar: () => apiRequest<UserProfile>("/auth/profile/avatar", { method: "DELETE" }),
  loadAvatar: async () => URL.createObjectURL((await apiDownload("/auth/profile/avatar")).blob),
  logout: () => apiRequest<void>("/auth/logout", { method: "POST" }),
  changePassword: (oldPassword: string, newPassword: string) =>
    apiRequest<void>("/auth/change-password", { method: "POST", ...jsonBody({ oldPassword, newPassword }) }),
};
