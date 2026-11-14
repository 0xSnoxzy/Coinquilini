package it.coinquilini.dao;

import it.coinquilini.model.House;

public interface HouseDAO {
    void save(House house);
    House findById(Long id);
    House findByInviteCode(String inviteCode);
    House findActiveByUserId(Long userId);
}
