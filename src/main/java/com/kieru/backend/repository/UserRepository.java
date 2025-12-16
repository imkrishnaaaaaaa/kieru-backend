package com.kieru.backend.repository;

import com.kieru.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface  UserRepository extends JpaRepository<User, String> {

    String getSubscriptionById(String OwnerId);

}

