package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransportExecutionRegistryTest
{
	@Test
	public void objectExecutorRequiresAnExecutableInteraction()
	{
		WorldPoint origin = new WorldPoint(3200, 3200, 0);
		WorldPoint destination = new WorldPoint(3200, 3200, 1);
		Transport executable = new Transport(
			origin, destination, "Upstairs", TransportType.TRANSPORT, false,
			"Climb-up", "Staircase", 16671);
		Transport missingObject = new Transport(
			origin, destination, "Upstairs", TransportType.TRANSPORT, false, 1);

		assertEquals(TransportExecutionRegistry.Executor.OBJECT,
			TransportExecutionRegistry.executorFor(executable).orElse(null));
		assertFalse(TransportExecutionRegistry.canExecute(missingObject));
	}

	@Test
	public void barrowsDigExecutorRequiresAnExactMoundMappingAndSpade()
	{
		WorldPoint origin = new WorldPoint(3564, 3291, 0);
		WorldPoint destination = new WorldPoint(3559, 9703, 3);
		Transport valid = new Transport(
			origin, destination, "Ahrim's Barrow", TransportType.TRANSPORT, true,
			"Dig", "Barrow", 0, 3);
		valid.setItemIdRequirements(Set.of(Set.of(ItemID.SPADE)));

		assertEquals(TransportExecutionRegistry.Executor.BARROWS_DIG,
			TransportExecutionRegistry.executorFor(valid).orElse(null));

		Transport wrongDestination = new Transport(
			origin, new WorldPoint(3558, 9718, 3), "Wrong crypt", TransportType.TRANSPORT, true,
			"Dig", "Barrow", 0, 3);
		wrongDestination.setItemIdRequirements(Set.of(Set.of(ItemID.SPADE)));
		Transport missingSpade = new Transport(
			origin, destination, "Missing spade", TransportType.TRANSPORT, true,
			"Dig", "Barrow", 0, 3);

		assertFalse(TransportExecutionRegistry.canExecute(wrongDestination));
		assertFalse(TransportExecutionRegistry.canExecute(missingSpade));
	}

	@Test
	public void spellExecutorMatchesTheActualMagicActionCatalog()
	{
		assertTrue(TransportExecutionRegistry.canExecute(spell("Lumbridge Home Teleport")));
		assertTrue(TransportExecutionRegistry.canExecute(spell("Edgeville Home Teleport")));
		assertTrue(TransportExecutionRegistry.canExecute(spell("Lunar Home Teleport")));
		assertTrue(TransportExecutionRegistry.canExecute(spell("Arceuus Home Teleport")));
		assertTrue(TransportExecutionRegistry.canExecute(spell("Varrock Teleport: Grand Exchange")));
		assertFalse(TransportExecutionRegistry.canExecute(spell("Unknown Home Teleport")));
	}

	@Test
	public void homeTeleportMappingIsExactAndSharedWithExecution()
	{
		for (TransportExecutionRegistry.HomeTeleport homeTeleport
			: TransportExecutionRegistry.HomeTeleport.values())
		{
			assertEquals(homeTeleport,
				TransportExecutionRegistry.homeTeleportFor(homeTeleport.getDisplayName()).orElse(null));
			assertEquals(homeTeleport,
				TransportExecutionRegistry.homeTeleportFor(
					"  " + homeTeleport.getDisplayName().toUpperCase(Locale.ROOT) + "  ").orElse(null));
		}
		assertFalse(TransportExecutionRegistry.homeTeleportFor("Lumbridge Home Teleport: Fake").isPresent());
	}

	@Test
	public void balloonExecutorRequiresAnExactKnownNetworkRow()
	{
		WorldPoint origin = new WorldPoint(2461, 3111, 0);
		WorldPoint destination = new WorldPoint(3299, 3482, 0);
		Transport valid = new Transport(
			origin, destination, "Varrock", TransportType.HOT_AIR_BALLOON, true,
			"Use", "Basket", 19129, 7);
		assertEquals(TransportExecutionRegistry.Executor.HOT_AIR_BALLOON,
			TransportExecutionRegistry.executorFor(valid).orElse(null));

		Transport unknownDestination = new Transport(
			origin, destination, "Unknown", TransportType.HOT_AIR_BALLOON, true,
			"Use", "Basket", 19129, 7);
		Transport unknownObject = new Transport(
			origin, destination, "Varrock", TransportType.HOT_AIR_BALLOON, true,
			"Use", "Basket", 99999, 7);
		assertFalse(TransportExecutionRegistry.canExecute(unknownDestination));
		assertFalse(TransportExecutionRegistry.canExecute(unknownObject));
	}

	@Test
	public void terminalTravelExecutorDoesNotAssumeTheCatalogTargetIsAnNpc()
	{
		WorldPoint origin = new WorldPoint(3271, 3144, 0);
		WorldPoint destination = new WorldPoint(3148, 2843, 0);
		for (TransportType type : List.of(TransportType.SHIP, TransportType.NPC, TransportType.BOAT))
		{
			Transport transport = new Transport(
				origin, destination, "", type, true,
				"Board", "Ferry", 41311, 8);
			assertEquals(TransportExecutionRegistry.Executor.TERMINAL_TRAVEL,
				TransportExecutionRegistry.executorFor(transport).orElse(null));
			assertEquals(TransportExecutionRegistry.TerminalTravelMode.DIRECT,
				TransportExecutionRegistry.terminalTravelModeFor(transport).orElse(null));
		}
	}

	@Test
	public void terminalTravelModesFailClosedForUnimplementedDestinationSelection()
	{
		WorldPoint origin = new WorldPoint(1342, 3645, 0);
		Transport multiDestinationBoat = new Transport(
			origin, new WorldPoint(1408, 3612, 0), "Shayzien", TransportType.BOAT, true,
			"Board", "Boaty", 33614, 5);
		Transport mountainGuide = new Transport(
			new WorldPoint(1277, 3558, 0), new WorldPoint(1401, 3536, 0),
			"The Shayzien Outpost", TransportType.NPC, true,
			"Travel", "Mountain Guide", 24190, 4);

		assertFalse(TransportExecutionRegistry.canExecute(multiDestinationBoat));
		assertFalse(TransportExecutionRegistry.terminalTravelModeFor(multiDestinationBoat).isPresent());
		assertEquals(TransportExecutionRegistry.TerminalTravelMode.DIALOGUE_DESTINATION,
			TransportExecutionRegistry.terminalTravelModeFor(mountainGuide).orElse(null));
		assertEquals(TransportExecutionRegistry.Executor.TERMINAL_TRAVEL,
			TransportExecutionRegistry.executorFor(mountainGuide).orElse(null));
	}

	@Test
	public void resourceCatalogHasOnlyExplicitTerminalExecutionDebt()
	{
		Map<WorldPoint, Set<Transport>> catalog = Transport.loadAllFromResources();
		List<Transport> unsupported = catalog.values().stream()
			.flatMap(Set::stream)
			.filter(transport -> !TransportExecutionRegistry.canExecute(transport))
			.collect(Collectors.toList());

		assertEquals("only explicitly audited terminal rows may remain fail-closed: " + describe(unsupported),
			35, unsupported.size());
		assertTrue("non-terminal execution debt: " + describe(unsupported),
			unsupported.stream().allMatch(transport ->
				transport.getType() == TransportType.SHIP
					|| transport.getType() == TransportType.NPC
					|| transport.getType() == TransportType.BOAT));
		Map<String, Long> debtByInteraction = unsupported.stream().collect(Collectors.groupingBy(
			transport -> transport.getType() + ":" + transport.getAction() + ":" + transport.getName(),
			Collectors.counting()));
		assertEquals(Map.of(
			"BOAT:Board:Boat", 18L,
			"BOAT:Board:Boaty", 12L,
			"BOAT:Talk-to:Pirate Pete", 2L,
			"SHIP:Talk-to:Captain Shanks", 3L), debtByInteraction);
		assertEquals("all teleport spells must have a registered executor",
			0L, unsupported.stream()
				.filter(transport -> transport.getType() == TransportType.TELEPORTATION_SPELL)
				.count());
		assertEquals("every expanded hot-air-balloon edge must use the dedicated executor",
			225L, catalog.values().stream()
				.flatMap(Set::stream)
				.filter(transport -> transport.getType() == TransportType.HOT_AIR_BALLOON)
				.filter(transport -> TransportExecutionRegistry.executorFor(transport)
					.orElse(null) == TransportExecutionRegistry.Executor.HOT_AIR_BALLOON)
				.count());
	}

	@Test
	public void numberedRowboatMenusAreDialogueDestinations()
	{
		// Fossil Island camp rowboat: one object, a numbered menu, rows in both boats.tsv and ships.tsv.
		WorldPoint camp = new WorldPoint(3724, 3807, 0);
		for (TransportType type : List.of(TransportType.BOAT, TransportType.SHIP))
		{
			Transport rowboat = new Transport(
				camp, new WorldPoint(3734, 3893, 0), "2. North of Island", type, true,
				"Travel", "Rowboat", 30914, 4);
			assertEquals(TransportExecutionRegistry.TerminalTravelMode.DIALOGUE_DESTINATION,
				TransportExecutionRegistry.terminalTravelModeFor(rowboat).orElse(null));
			assertTrue(TransportExecutionRegistry.canExecute(rowboat));
		}
		Transport unnumbered = new Transport(
			camp, new WorldPoint(3734, 3893, 0), "North of Island", TransportType.BOAT, true,
			"Travel", "Rowboat", 30914, 4);
		assertFalse("an unnumbered rowboat label still fails closed",
			TransportExecutionRegistry.canExecute(unnumbered));
	}

	/**
	 * Read in-game 2026-10-31: 1. Row to the barge, 2. Row to the north of the island, 3. Row to the barge
	 * and travel to the Digsite, 4. Row out to sea, north of the island, 5. Cancel.
	 */
	@Test
	public void fossilIslandCampRowboatKeysMatchTheInGameMenu()
	{
		WorldPoint camp = new WorldPoint(3724, 3807, 0);
		Map<WorldPoint, Character> keyByDestination = Transport.loadAllFromResources()
			.getOrDefault(camp, Set.of()).stream()
			.filter(transport -> transport.getObjectId() == 30914)
			.collect(Collectors.toMap(Transport::getDestination,
				transport -> TransportExecutionRegistry.numberedOptionKey(transport.getDisplayInfo())));

		assertEquals(Map.of(
			new WorldPoint(3734, 3893, 0), '2',
			new WorldPoint(3362, 3445, 0), '3',
			new WorldPoint(3763, 3899, 0), '4'), keyByDestination);
	}

	@Test
	public void numberedOptionKeyReadsOnlyALeadingDigitAndDot()
	{
		assertEquals(Character.valueOf('3'), TransportExecutionRegistry.numberedOptionKey("3. Digsite"));
		assertEquals(Character.valueOf('4'), TransportExecutionRegistry.numberedOptionKey("4. To Sea"));
		assertEquals(null, TransportExecutionRegistry.numberedOptionKey("Musa Point"));
		assertEquals(null, TransportExecutionRegistry.numberedOptionKey("0. Nowhere"));
		assertEquals(null, TransportExecutionRegistry.numberedOptionKey("12 Things"));
		assertEquals(null, TransportExecutionRegistry.numberedOptionKey("1"));
		assertEquals(null, TransportExecutionRegistry.numberedOptionKey(null));
	}

	/**
	 * DIRECT means "the click itself picks the destination". An origin whose one action leads to several
	 * destinations opens a menu, so a DIRECT row there is planned but can never land (Fossil Island
	 * camp rowboat -> Digsite, 2026-10-31).
	 */
	@Test
	public void noDirectTerminalTravelSharesItsActionWithAnotherDestination()
	{
		Map<String, List<Transport>> byClick = Transport.loadAllFromResources().values().stream()
			.flatMap(Set::stream)
			.filter(transport -> TransportExecutionRegistry.executorFor(transport).orElse(null)
				== TransportExecutionRegistry.Executor.TERMINAL_TRAVEL)
			.collect(Collectors.groupingBy(transport -> transport.getOrigin() + "|" + transport.getObjectId()
				+ "|" + transport.getAction().toLowerCase()));

		List<Transport> ambiguous = byClick.values().stream()
			.filter(rows -> rows.stream().map(Transport::getDestination).distinct().count() > 1)
			.flatMap(List::stream)
			.filter(transport -> TransportExecutionRegistry.terminalTravelModeFor(transport).orElse(null)
				== TransportExecutionRegistry.TerminalTravelMode.DIRECT)
			.collect(Collectors.toList());

		assertTrue("a DIRECT click cannot choose between destinations: " + describe(ambiguous),
			ambiguous.isEmpty());
	}

	private static Transport spell(String displayInfo)
	{
		return new Transport(
			null, new WorldPoint(3200, 3200, 0), displayInfo,
			TransportType.TELEPORTATION_SPELL, false, 1);
	}

	private static String describe(List<Transport> transports)
	{
		return transports.stream()
			.map(transport -> transport.getType() + ":" + transport.getDisplayInfo()
				+ "@" + transport.getOrigin() + "->" + transport.getDestination())
			.collect(Collectors.joining(", "));
	}
}
