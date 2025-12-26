package com.faforever.server.runtime;

import jakarta.enterprise.context.NormalScope;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Inherited
@NormalScope
@Target({TYPE, METHOD, FIELD})
@Retention(RUNTIME)
public @interface ConnectionScoped {
}
