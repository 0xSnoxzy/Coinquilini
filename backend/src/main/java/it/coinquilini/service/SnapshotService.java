package it.coinquilini.service;

import it.coinquilini.dao.HouseDAO;
import it.coinquilini.dao.MembershipDAO;
import it.coinquilini.model.House;
import it.coinquilini.model.Snapshot;
import it.coinquilini.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SnapshotService {
    private final AuthService auth;
    private final HouseDAO houses;
    private final MembershipDAO memberships;

    public SnapshotService(AuthService auth, HouseDAO houses, MembershipDAO memberships) {
        this.auth = auth;
        this.houses = houses;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public Snapshot load(HttpSession session) {
        User user = auth.currentUser(session);
        House house = houses.findActiveByUserId(user.getId());
        return Snapshot.forHouse(user, house,
                house == null ? List.of() : memberships.findMembersByHouseId(house.getId()));
    }
}
