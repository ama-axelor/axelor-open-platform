/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2019 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.auth;

import com.axelor.app.AppSettings;
import com.axelor.auth.cas.AuthCasModule;
import com.axelor.auth.ldap.AuthLdapModule;
import com.axelor.auth.pac4j.AuthPac4jModule;
import com.axelor.auth.pac4j.AuthPac4jModuleOidc;
import com.axelor.auth.pac4j.AuthPac4jModuleSaml2;
import com.axelor.auth.pac4j.AuthPac4jObserver;
import com.axelor.db.JpaSecurity;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.guice.ShiroModule;
import org.apache.shiro.mgt.SecurityManager;

public class AuthModule extends AbstractModule {

  private ServletContext context;

  public AuthModule() {}

  public AuthModule(ServletContext context) {
    this.context = context;
  }

  @Override
  protected final void configure() {

    // bind security service
    bind(JpaSecurity.class).toProvider(AuthSecurity.class);

    // observe authentication-related events
    bind(AuthObserver.class);

    // non-web environment (cli or unit tests)
    if (context == null) {
      install(new MyShiroModule());
      return;
    }

    // CAS
    if (AuthCasModule.isEnabled()) {
      install(new AuthCasModule(context));
      return;
    }

    // LDAP
    if (AuthLdapModule.isEnabled()) {
      install(new AuthLdapModule(context));
      return;
    }

    final AppSettings settings = AppSettings.get();
    final boolean saveUsersFromCentral =
        settings.getBoolean(AuthPac4jModule.CONFIG_AUTH_SAVE_USERS_FROM_CENTRAL, false);

    if (saveUsersFromCentral) {
      bind(AuthPac4jObserver.class);
    }

    // OpenID Connect
    if (AuthPac4jModuleOidc.isEnabled()) {
      install(new AuthPac4jModuleOidc(context));
      return;
    }

    // SAML2
    if (AuthPac4jModuleSaml2.isEnabled()) {
      install(new AuthPac4jModuleSaml2(context));
      return;
    }

    // default
    install(new AuthWebModule(context));
  }

  static final class MyShiroModule extends ShiroModule {

    @Override
    protected void configureShiro() {
      this.bindRealm().to(AuthRealm.class);
      this.bind(Initializer.class).asEagerSingleton();
    }
  }

  @Singleton
  public static class Initializer {

    @Inject
    public Initializer(Injector injector) {
      SecurityManager sm = injector.getInstance(SecurityManager.class);
      SecurityUtils.setSecurityManager(sm);
    }
  }
}
