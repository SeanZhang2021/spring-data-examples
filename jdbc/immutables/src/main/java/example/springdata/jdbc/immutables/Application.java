/*
 * Copyright 2021 the original author or authors.
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
package example.springdata.jdbc.immutables;

import java.sql.ResultSet;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jdbc.core.convert.BasicJdbcConverter;
import org.springframework.data.jdbc.core.convert.DefaultJdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.Identifier;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.RelationResolver;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.mapping.PersistentPropertyPathExtension;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.util.ClassUtils;

/**
 * Configuration stub.
 *
 * @author Mark Paluch
 */
@SpringBootApplication
class Application {

	/**
	 * Name scheme how Immutables generates implementations from interface/class definitions.
	 */
	public static final String IMMUTABLE_IMPLEMENTATION_CLASS = "%s.Immutable%s";

	@Configuration
	static class ImmutablesJdbcConfiguration extends AbstractJdbcConfiguration {

		private final ResourceLoader resourceLoader;

		public ImmutablesJdbcConfiguration(ResourceLoader resourceLoader) {
			this.resourceLoader = resourceLoader;
		}

		/**
		 * {@link JdbcConverter} that redirects entities to be instantiated towards the implementation. See
		 * {@link #IMMUTABLE_IMPLEMENTATION_CLASS} and
		 * {@link #getImplementationEntity(JdbcMappingContext, RelationalPersistentEntity)}.
		 *
		 * @param mappingContext
		 * @param operations
		 * @param relationResolver
		 * @param conversions
		 * @param dialect
		 * @return
		 */
		@Override
		public JdbcConverter jdbcConverter(JdbcMappingContext mappingContext, NamedParameterJdbcOperations operations,
				RelationResolver relationResolver, JdbcCustomConversions conversions, Dialect dialect) {

			var jdbcTypeFactory = new DefaultJdbcTypeFactory(operations.getJdbcOperations());

			return new BasicJdbcConverter(mappingContext, relationResolver, conversions, jdbcTypeFactory,
					dialect.getIdentifierProcessing()) {

				@Override
				public <T> T mapRow(RelationalPersistentEntity<T> entity, ResultSet resultSet, Object key) {
					return super.mapRow(getImplementationEntity(mappingContext, entity), resultSet, key);
				}

				@Override
				public <T> T mapRow(PersistentPropertyPathExtension path, ResultSet resultSet, Identifier identifier,
						Object key) {

					return super.mapRow(new DelegatePersistentPropertyPathExtension(mappingContext,
							path.getRequiredPersistentPropertyPath(), getImplementationEntity(mappingContext, path.getLeafEntity())),
							resultSet, identifier, key);
				}
			};
		}

		@SuppressWarnings("unchecked")
		private <T> RelationalPersistentEntity<T> getImplementationEntity(JdbcMappingContext mappingContext,
				RelationalPersistentEntity<T> entity) {

			var type = entity.getType();
			if (type.isInterface()) {

				var immutableClass = String.format(IMMUTABLE_IMPLEMENTATION_CLASS, type.getPackageName(), type.getSimpleName());
				if (ClassUtils.isPresent(immutableClass, resourceLoader.getClassLoader())) {

					return (RelationalPersistentEntity<T>) mappingContext
							.getPersistentEntity(ClassUtils.resolveClassName(immutableClass, resourceLoader.getClassLoader()));
				}

			}
			return entity;
		}
	}

	/**
	 * Redirect {@link #getLeafEntity()} to a different entity type.
	 */
	static class DelegatePersistentPropertyPathExtension extends PersistentPropertyPathExtension {

		private final RelationalPersistentEntity<?> leafEntity;

		public DelegatePersistentPropertyPathExtension(
				MappingContext<? extends RelationalPersistentEntity<?>, ? extends RelationalPersistentProperty> context,
				PersistentPropertyPath<? extends RelationalPersistentProperty> path, RelationalPersistentEntity<?> leafEntity) {
			super(context, path);
			this.leafEntity = leafEntity;
		}

		@Override
		public RelationalPersistentEntity<?> getLeafEntity() {
			return leafEntity;
		}
	}

}
