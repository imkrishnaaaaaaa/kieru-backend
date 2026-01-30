package com.kieru.backend.repository;

import com.kieru.backend.entity.User;
import com.kieru.backend.util.KieruUtil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @Query("SELECT u.subscription FROM User u WHERE u.id = :id")
    KieruUtil.SubscriptionPlan findSubscriptionPlanById(@Param("id") String id);

    @Modifying // Required for DELETE/UPDATE queries
    @Query("DELETE FROM User u WHERE u.subscription IN :plans AND " +
            "(u.lastLoginAt < :cutoff OR (u.lastLoginAt IS NULL AND u.joinedAt < :cutoff))")
    int deleteInactiveUsers(
            @Param("plans") List<KieruUtil.SubscriptionPlan> plans,
            @Param("cutoff") Instant cutoff
    );
}