package com.kieru.backend.repository;

import com.kieru.backend.entity.User;
import com.kieru.backend.util.KieruUtil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @Query("SELECT u.subscription FROM User u WHERE u.id = :id")
    KieruUtil.SubscriptionPlan findSubscriptionPlanById(@Param("id") String id);

}