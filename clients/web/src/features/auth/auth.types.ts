export type Role = "STUDENT" | "TEACHER" | "ADMIN";

export type AuthUser = {
  id: number;
  loginName: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  mustChangePassword: boolean;
};

export type LoginResponse = {
  token: string;
  user: AuthUser;
};

export type LoginInput = {
  account: string;
  password: string;
};

export type AuthErrorCode =
  | "INVALID_CREDENTIALS"
  | "ACCOUNT_DISABLED"
  | "SESSION_EXPIRED"
  | "NETWORK_ERROR"
  | "UNKNOWN";

export type AuthError = Error & {
  code?: AuthErrorCode;
  status?: number;
};
