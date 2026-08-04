import { describe, expect, it } from "vitest";
import { homeForRole, normalizeAuthError } from "./auth.utils";

describe("authentication utilities", () => {
  it("maps each role to its dashboard", () => {
    expect(homeForRole("STUDENT")).toBe("/student/dashboard");
    expect(homeForRole("TEACHER")).toBe("/teacher/dashboard");
    expect(homeForRole("ADMIN")).toBe("/admin/dashboard");
  });

  it("normalizes disabled and invalid login responses", () => {
    expect(normalizeAuthError({ message: "disabled", status: 403 }).code).toBe("ACCOUNT_DISABLED");
    expect(normalizeAuthError({ message: "invalid", status: 401 }).code).toBe("INVALID_CREDENTIALS");
  });
});
