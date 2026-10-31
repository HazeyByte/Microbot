package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fossil Island -> GE, 2026-10-31: the world-map click planned the rowboat route before the bank
 * visit; the walker withdrew a Ring of wealth, refreshed transports, then reused the finished
 * pre-withdraw route because it still pointed at the same target.
 */
public class Rs2WalkerStaleRouteReuseTest
{
	private static final WorldPoint TARGET = new WorldPoint(3168, 3488, 0);

	private static Rs2ActiveRouteStatus readyRouteTo(WorldPoint target)
	{
		Rs2ActiveRouteStatus status = mock(Rs2ActiveRouteStatus.class);
		when(status.isReady()).thenReturn(true);
		when(status.getTargets()).thenReturn(Set.of(target));
		return status;
	}

	@Test
	public void readyRouteToTheSameTargetIsReusedWhenTransportsAreUnchanged()
	{
		assertTrue(Rs2Walker.canReuseActiveRoute(readyRouteTo(TARGET), TARGET, true));
	}

	@Test
	public void routePlannedBeforeTheTransportsChangedIsNotReused()
	{
		assertFalse("a route planned before the ring was withdrawn must be replanned",
			Rs2Walker.canReuseActiveRoute(readyRouteTo(TARGET), TARGET, false));
	}

	@Test
	public void routeToAnotherTargetIsNotReused()
	{
		assertFalse(Rs2Walker.canReuseActiveRoute(readyRouteTo(new WorldPoint(3000, 3000, 0)), TARGET, true));
	}

	@Test
	public void pathfinderRemembersTheTransportRefreshItWasPlannedUnder()
	{
		PathfinderConfig config = mock(PathfinderConfig.class);
		when(config.getLastRefreshKey()).thenReturn(42);
		when(config.getLastItemFingerprint()).thenReturn(1001);

		Pathfinder pathfinder = new Pathfinder(config, new WorldPoint(3724, 3807, 0), TARGET);
		when(config.getLastRefreshKey()).thenReturn(7);
		when(config.getLastItemFingerprint()).thenReturn(2002);

		assertEquals(42, pathfinder.getPlannedRefreshKey());
		assertEquals(1001, pathfinder.getPlannedItemFingerprint());
	}

	@Test
	public void routeIsCurrentOnlyWhileRefreshAndItemsBothMatch() throws Exception
	{
		PathfinderConfig config = mock(PathfinderConfig.class);
		when(config.getLastRefreshKey()).thenReturn(42);
		when(config.getLastItemFingerprint()).thenReturn(1001);
		Pathfinder planned = new Pathfinder(config, new WorldPoint(3724, 3807, 0), TARGET);

		try (MockedStatic<ShortestPathPlugin> plugin = Mockito.mockStatic(ShortestPathPlugin.class))
		{
			plugin.when(ShortestPathPlugin::getPathfinderMutex).thenReturn(new Object());
			plugin.when(ShortestPathPlugin::getPathfinder).thenReturn(planned);
			plugin.when(ShortestPathPlugin::getPathfinderConfig).thenReturn(config);

			when(config.currentItemFingerprint()).thenReturn(1001);
			assertTrue(Rs2PathApi.isActiveRoutePlannedWithCurrentTransports());

			// Ring of wealth withdrawn, no refresh yet: items differ.
			when(config.currentItemFingerprint()).thenReturn(1002);
			assertFalse(Rs2PathApi.isActiveRoutePlannedWithCurrentTransports());

			// The post-withdraw inventory-only refresh: settings key differs.
			when(config.currentItemFingerprint()).thenReturn(1001);
			when(config.getLastRefreshKey()).thenReturn(43);
			assertFalse(Rs2PathApi.isActiveRoutePlannedWithCurrentTransports());
		}
	}
}
