package it.coinquilini.dao;

import it.coinquilini.model.Member;
import it.coinquilini.model.Membership;
import java.util.List;

public interface MembershipDAO {
    void save(Membership membership);
    Membership findActiveByUserId(Long userId);
    List<Member> findMembersByHouseId(Long houseId);
}
