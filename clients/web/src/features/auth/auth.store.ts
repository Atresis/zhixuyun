import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { authApi } from "../../api/auth";
import { setUnauthorizedHandler } from "../../api/client";
import type { AuthUser, UserProfile } from "./auth.types";
import { clearStoredUser, clearToken, homeForRole, normalizeAuthError, readStoredUser, readToken, saveStoredUser, saveToken } from "./auth.utils";

export const useAuthStore = defineStore("auth", () => {
  const user = ref<AuthUser | null>(readStoredUser());
  const initialized = ref(false);
  const loading = ref(false);
  const error = ref<ReturnType<typeof normalizeAuthError> | null>(null);
  const isAuthenticated = computed(() => Boolean(user.value && readToken()));

  async function login(account: string, password: string, remember: boolean): Promise<AuthUser> {
    loading.value = true;
    error.value = null;
    try {
      const result = await authApi.login(account.trim(), password);
      if (!result.token || !result.user?.role || !result.user.enabled) {
        const disabled = result.user && result.user.enabled === false;
        throw Object.assign(new Error(disabled ? "账号已被禁用，请联系管理员" : "登录响应无效"), { code: disabled ? "ACCOUNT_DISABLED" : "UNKNOWN" });
      }
      saveToken(result.token, remember);
      saveStoredUser(result.user, remember);
      user.value = result.user;
      return result.user;
    } catch (cause) {
      const normalized = normalizeAuthError(cause);
      error.value = normalized;
      throw normalized;
    } finally {
      loading.value = false;
    }
  }

  async function restore(): Promise<AuthUser | null> {
    if (initialized.value) return user.value;
    initialized.value = true;
    if (!readToken()) {
      clearSession();
      return null;
    }
    try {
      const current = await authApi.me();
      if (!current.enabled) throw Object.assign(new Error("账号已被禁用，请联系管理员"), { code: "ACCOUNT_DISABLED", status: 403 });
      user.value = current;
      saveStoredUser(current, Boolean(localStorage.getItem("zhixuyun_access_token")));
      return current;
    } catch {
      clearSession();
      return null;
    }
  }

  async function logout(): Promise<void> {
    try {
      if (readToken()) await authApi.logout();
    } catch {
      // Local state must be cleared even when the server is unavailable.
    } finally {
      clearSession();
    }
  }

  async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await authApi.changePassword(oldPassword, newPassword);
    clearSession();
  }

  function clearSession(): void {
    clearToken();
    clearStoredUser();
    user.value = null;
    error.value = null;
  }

  function destination(): string {
    return user.value ? homeForRole(user.value.role) : "/login";
  }

  function applyProfile(profile: UserProfile): void {
    if (!user.value) return;
    user.value = { ...user.value, displayName: profile.displayName };
    saveStoredUser(user.value, Boolean(localStorage.getItem("zhixuyun_access_token")));
  }

  setUnauthorizedHandler(clearSession);

  return { user, initialized, loading, error, isAuthenticated, login, restore, logout, changePassword, clearSession, destination, applyProfile };
});
