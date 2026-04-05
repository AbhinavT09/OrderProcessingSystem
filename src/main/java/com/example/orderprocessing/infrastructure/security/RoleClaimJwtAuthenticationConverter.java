package com.example.orderprocessing.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * JWT-to-authority converter for interface-layer authorization.
 *
 * <p><b>Architecture role:</b> infrastructure security adapter used by Spring Resource Server.</p>
 *
 * <p><b>Resilience and idempotency context:</b> stateless conversion logic; repeated token parsing
 * yields deterministic authority mapping with no side effects.</p>
 */
public class RoleClaimJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    /**
     * Executes convert.
     * @param jwt input argument used by this operation
     * @return operation result
     */
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
