/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.r2dbc.config;

import io.r2dbc.spi.ConnectionFactory;

import java.util.Collections;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.CustomConversions.StoreConversions;
import org.springframework.data.r2dbc.dialect.Database;
import org.springframework.data.r2dbc.dialect.Dialect;
import org.springframework.data.r2dbc.function.DatabaseClient;
import org.springframework.data.r2dbc.function.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.function.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.support.R2dbcExceptionTranslator;
import org.springframework.data.r2dbc.support.SqlErrorCodeR2dbcExceptionTranslator;
import org.springframework.data.relational.core.conversion.BasicRelationalConverter;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.util.Assert;

/**
 * Base class for Spring Data R2DBC configuration containing bean declarations that must be registered for Spring Data
 * R2DBC to work.
 *
 * @author Mark Paluch
 * @see ConnectionFactory
 * @see DatabaseClient
 * @see org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
 */
@Configuration
public abstract class AbstractR2dbcConfiguration {

	/**
	 * Return a R2DBC {@link ConnectionFactory}. Annotate with {@link Bean} in case you want to expose a
	 * {@link ConnectionFactory} instance to the {@link org.springframework.context.ApplicationContext}.
	 *
	 * @return the configured {@link ConnectionFactory}.
	 */
	public abstract ConnectionFactory connectionFactory();

	/**
	 * Return a {@link Dialect} for the given {@link ConnectionFactory}. This method attempts to resolve a {@link Dialect}
	 * from {@link io.r2dbc.spi.ConnectionFactoryMetadata}. Override this method to specify a dialect instead of
	 * attempting to resolve one.
	 *
	 * @param connectionFactory the configured {@link ConnectionFactory}.
	 * @return the resolved {@link Dialect}.
	 * @throws UnsupportedOperationException if the {@link Dialect} cannot be determined.
	 */
	public Dialect getDialect(ConnectionFactory connectionFactory) {

		return Database.findDatabase(connectionFactory)
				.orElseThrow(() -> new UnsupportedOperationException(
						String.format("Cannot determine a dialect for %s using %s. Please provide a Dialect.",
								connectionFactory.getMetadata().getName(), connectionFactory)))
				.defaultDialect();
	}

	/**
	 * Register a {@link DatabaseClient} using {@link #connectionFactory()} and {@link RelationalMappingContext}.
	 *
	 * @return must not be {@literal null}.
	 * @throws IllegalArgumentException if any of the required args is {@literal null}.
	 */
	@Bean
	public DatabaseClient databaseClient(ReactiveDataAccessStrategy dataAccessStrategy,
			R2dbcExceptionTranslator exceptionTranslator) {

		Assert.notNull(dataAccessStrategy, "DataAccessStrategy must not be null!");
		Assert.notNull(exceptionTranslator, "ExceptionTranslator must not be null!");

		return DatabaseClient.builder() //
				.connectionFactory(connectionFactory()) //
				.dataAccessStrategy(dataAccessStrategy) //
				.exceptionTranslator(exceptionTranslator) //
				.build();
	}

	/**
	 * Register a {@link RelationalMappingContext} and apply an optional {@link NamingStrategy}.
	 *
	 * @param namingStrategy optional {@link NamingStrategy}. Use {@link NamingStrategy#INSTANCE} as fallback.
	 * @param r2dbcCustomConversions customized R2DBC conversions.
	 * @return must not be {@literal null}.
	 * @throws IllegalArgumentException if any of the required args is {@literal null}.
	 */
	@Bean
	public RelationalMappingContext r2dbcMappingContext(Optional<NamingStrategy> namingStrategy,
			R2dbcCustomConversions r2dbcCustomConversions) {

		Assert.notNull(namingStrategy, "NamingStrategy must not be null!");

		RelationalMappingContext relationalMappingContext = new RelationalMappingContext(
				namingStrategy.orElse(NamingStrategy.INSTANCE));
		relationalMappingContext.setSimpleTypeHolder(r2dbcCustomConversions.getSimpleTypeHolder());

		return relationalMappingContext;
	}

	/**
	 * Creates a {@link ReactiveDataAccessStrategy} using the configured {@link #r2dbcMappingContext(Optional, R2dbcCustomConversions)}
	 * RelationalMappingContext}.
	 *
	 * @param mappingContext the configured {@link RelationalMappingContext}.
	 * @param r2dbcCustomConversions customized R2DBC conversions.
	 * @return must not be {@literal null}.
	 * @see #r2dbcMappingContext(Optional, R2dbcCustomConversions)
	 * @see #getDialect(ConnectionFactory)
	 * @throws IllegalArgumentException if any of the {@literal mappingContext} is {@literal null}.
	 */
	@Bean
	public ReactiveDataAccessStrategy reactiveDataAccessStrategy(RelationalMappingContext mappingContext,
			R2dbcCustomConversions r2dbcCustomConversions) {

		Assert.notNull(mappingContext, "MappingContext must not be null!");

		BasicRelationalConverter converter = new BasicRelationalConverter(mappingContext, r2dbcCustomConversions);

		return new DefaultReactiveDataAccessStrategy(getDialect(connectionFactory()), converter);
	}

	/**
	 * Register custom {@link Converter}s in a {@link CustomConversions} object if required. These
	 * {@link CustomConversions} will be registered with the {@link BasicRelationalConverter} and
	 * {@link #r2dbcMappingContext(Optional, R2dbcCustomConversions)}. Returns an empty {@link R2dbcCustomConversions}
	 * instance by default.
	 *
	 * @return must not be {@literal null}.
	 */
	@Bean
	public R2dbcCustomConversions r2dbcCustomConversions() {

		Dialect dialect = getDialect(connectionFactory());
		StoreConversions storeConversions = StoreConversions.of(dialect.getSimpleTypeHolder());
		return new R2dbcCustomConversions(storeConversions, Collections.emptyList());
	}

	/**
	 * Creates a {@link R2dbcExceptionTranslator} using the configured {@link #connectionFactory() ConnectionFactory}.
	 *
	 * @return must not be {@literal null}.
	 * @see #connectionFactory()
	 */
	@Bean
	public R2dbcExceptionTranslator exceptionTranslator() {
		return new SqlErrorCodeR2dbcExceptionTranslator(connectionFactory());
	}
}
