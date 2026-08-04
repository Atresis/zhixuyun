package cloud.zhixuyun.auth;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LoginRequest(@JsonAlias("loginName") String account, String password) {
    public String normalizedAccount() {
        return account == null ? "" : account.trim();
    }
}
