package com.atguigu.account.service;

public interface AccountService {

    /**
     * 從使用者帳戶中扣減
     * @param userId  使用者id
     * @param money   扣減金額
     */
    void debit(String userId, int money);
}
