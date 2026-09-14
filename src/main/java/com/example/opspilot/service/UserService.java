package com.example.opspilot.service;

import java.util.List;

import com.example.opspilot.dto.UserResponse;
import com.example.opspilot.entity.User;
import com.example.opspilot.repository.HelloRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final HelloRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll(Sort.by("id"))
            .stream()
            .map(UserResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        User user = findUserOrThrow(id);

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse create(String name) {
        User user = User.builder()
            .name(name)
            .build();
        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    @Transactional
    public UserResponse update(Long id, String name) {
        User user = findUserOrThrow(id);
        user.rename(name);

        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = findUserOrThrow(id);

        userRepository.delete(user);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"));
    }
}
