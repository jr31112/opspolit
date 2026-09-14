package com.example.opspilot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.opspilot.entity.User;

public interface HelloRepository extends JpaRepository<User, Long> {

}
