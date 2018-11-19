package org.springframework.data.r2dbc.dialect;

/**
 * Bind markers represent placeholders in SQL queries for substitution for an actual parameter. Using bind markers
 * allows creating safe queries so query strings are not required to contain escaped values but rather the driver
 * encodes parameter in the appropriate representation.
 * <p/>
 * {@link BindMarkers} is stateful and can be only used for a single binding pass of one or more parameters. It
 * maintains bind indexes/bind parameter names.
 *
 * @author Mark Paluch
 * @see BindMarker
 * @see BindMarkersFactory
 * @see io.r2dbc.spi.Statement#bind
 */
@FunctionalInterface
public interface BindMarkers {

	/**
	 * Creates a new {@link BindMarker}.
	 *
	 * @return a new {@link BindMarker}.
	 */
	BindMarker next();

	/**
	 * Creates a new {@link BindMarker} that accepts a {@code nameHint}. Implementations are allowed to consider/ignore
	 * the name hint to create more expressive bind markers.
	 *
	 * @param nameHint an optional name hint.
	 * @return a new {@link BindMarker}.
	 */
	default BindMarker next(String nameHint) {
		return next();
	}
}