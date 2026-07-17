package com.rta.dignify.service;

import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserDeviceToken;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DeviceTokenService {
    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(Long userId, String token, String environment) {
        User user = userRepository.getReferenceById(userId);
        userDeviceTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.reassign(user, environment),
                () -> userDeviceTokenRepository.save(UserDeviceToken.create(user, token, environment))
        );
    }
}
