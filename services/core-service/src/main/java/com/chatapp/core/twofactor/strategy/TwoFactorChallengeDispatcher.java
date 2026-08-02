package com.chatapp.core.twofactor.strategy;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.TwoFactorMethod;
import com.chatapp.core.base.entity.TwoFactorMethodEntity;
import com.chatapp.core.base.entity.UserEntity;

/**
 * Routes to the right {@link TwoFactorChallengeStrategy} by method — used by
 * `/auth/login/2fa/challenge` (send) and `/auth/login/2fa` (verify) once the user has
 * explicitly picked a method (never auto-picked at login; see AuthServiceImpl.login).
 */
@Component
public class TwoFactorChallengeDispatcher {

    private static final List<TwoFactorMethod> STRENGTH_PRIORITY = List.of(
            TwoFactorMethod.TOTP, TwoFactorMethod.EMAIL, TwoFactorMethod.SMS);

    private final Map<TwoFactorMethod, TwoFactorChallengeStrategy> strategies;

    public TwoFactorChallengeDispatcher(List<TwoFactorChallengeStrategy> strategies) {
        this.strategies = new EnumMap<>(TwoFactorMethod.class);
        strategies.forEach(strategy -> this.strategies.put(strategy.method(), strategy));
    }

    public void challenge(UserEntity user, TwoFactorMethodEntity config) {
        resolve(config.getMethod()).challenge(user, config);
    }

    public boolean verify(UserEntity user, TwoFactorMethodEntity config, String code) {
        return resolve(config.getMethod()).verify(user, config, code);
    }

    /** Strongest first (TOTP > EMAIL > SMS) — display order only, does not pick anything. */
    public List<String> sortByStrength(List<TwoFactorMethodEntity> methods) {
        return methods.stream()
                .sorted(Comparator.comparingInt(m -> STRENGTH_PRIORITY.indexOf(m.getMethod())))
                .map(m -> m.getMethod().name())
                .toList();
    }

    private TwoFactorChallengeStrategy resolve(TwoFactorMethod method) {
        TwoFactorChallengeStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new UnsupportedOperationException("No strategy implemented for 2FA method " + method);
        }
        return strategy;
    }
}
