package com.mehtank.androminion.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import com.mehtank.androminion.R;

import com.vdom.api.ActionCard;
import com.vdom.api.Card;
import com.vdom.api.TreasureCard;
import com.vdom.api.VictoryCard;
import com.vdom.comms.SelectCardOptions;
import com.vdom.comms.SelectCardOptions.PickType;
import com.vdom.comms.SelectCardOptions.ActionType;
import com.vdom.core.ActionCardImpl;
import com.vdom.core.CardList;
import com.vdom.core.Cards;
import com.vdom.core.Game;
import com.vdom.core.MoveContext;
import com.vdom.core.MoveContext.PileSelection;
import com.vdom.core.Player;
import com.vdom.core.QuickPlayPlayer;
/**
 * Class that you can use to play remotely.
 */
public abstract class IndirectPlayer extends QuickPlayPlayer {
    @SuppressWarnings("unused")
    private static final String TAG = "IndirectPlayerOrig";

    public abstract Card intToCard(int i);
    public abstract int cardToInt(Card card);
    public abstract int[] cardArrToIntArr(Card[] cards);
    public Card nameToCard(String o, Card[] cards) {
        for (Card c : cards)
            if (c.getName().equals(o))
                return c;
        return null;
    }

    public Card localNameToCard(String o, Card[] cards) {
        for (Card c : cards)
            if (Strings.getCardName(c).equals(o))
                return c;
        return null;
    }

    abstract protected String selectString(MoveContext context, String header, String[] s);
    abstract protected int selectOption(MoveContext context, Card card, Object[] options);
    abstract protected int[] orderCards(MoveContext context, int[] cards);
    abstract protected int[] orderCards(MoveContext context, int[] cards, String header);

    abstract protected Card[] pickCards(MoveContext context, String header, SelectCardOptions sco, int count, boolean exact);
    private Card pickACard(MoveContext context, String header, SelectCardOptions sco) {
        Card[] cs = pickCards(context, header, sco, 1, true);
        return (cs == null ? null : cs[0]);
    }

    @Override
    public boolean isAi() {
        return false;
    }

    public String selectString(MoveContext context, Card cardResponsible, String[] s) {
        return selectString(context, Strings.format(R.string.card_options_header, getCardName(cardResponsible)), s);
    }

    public String selectString(MoveContext context, int resId, Card cardResponsible, String[] s) {
        return selectString(context, getString(resId) + " [" + getCardName(cardResponsible) + "]", s);
    }

    public String getString(int id) {
        return Strings.getString(id);
    }

    public String getCardName(Card card) {
        return Strings.getCardName(card);
    }

    public String getActionString(ActionType action, Card cardResponsible, String opponentName) {
        return Strings.getActionString(action, cardResponsible, opponentName);
    }

    private String getActionString(ActionType action, Card cardResponsible) {
        return Strings.getActionString(action, cardResponsible, null);
    }

    private Card getCardFromHand(MoveContext context, SelectCardOptions sco) {
        return getCardFromHand(context, null, sco);
    }

    private Card getCardFromHand(MoveContext context, String header, SelectCardOptions sco) {
        Card[] cs = getFromHand(context, header, sco.setCount(1).exactCount());
        return (cs == null ? null : cs[0]);
    }

    private Card[] getFromHand(MoveContext context, SelectCardOptions sco) {
        return getFromHand(context, null, sco);
    }

    private Card[] getFromHand(MoveContext context, String header, SelectCardOptions sco) {
        sco =  sco.fromHand();
        CardList localHand = (context.player.isPossessed()) ? context.player.getHand() : getHand();
        if (localHand.size() == 0) {
            return null;
        } else if (sco.count == Integer.MAX_VALUE) {
            sco.setCount(localHand.size());
        } else if (sco.count < 0) {
            sco.setCount(localHand.size() + sco.count).exactCount();
        } else if (localHand.size() < sco.count && sco.exactCount) {
            sco.setCount(localHand.size());
        }

        ArrayList<Card> handList = new ArrayList<Card>();

        for (Card card : localHand) {
            if (sco.checkValid(card)) {
                handList.add(card);
                sco.addValidCard(cardToInt(card));
            }
        }

        if (sco.allowedCards.size() == 0)
            return null;
        else if (sco.allowedCards.size() == 1 || (sco.isAction && Collections.frequency(sco.allowedCards, sco.allowedCards.get(0)) == sco.allowedCards.size()))
            sco.defaultCardSelected = sco.allowedCards.get(0);

        Card[] tempCards = pickCards(context, header, sco, sco.count, sco.exactCount);
        if (tempCards == null)
            return null;

        // Hack to notify that "All" was selected
        if(tempCards.length == 0) {
            return tempCards;
        }

        for (int i=0; i<tempCards.length; i++)
            for (Card c : handList)
                if (c.equals(tempCards[i])) {
                    tempCards[i] = c;
                    handList.remove(c);
                    break;
                }

        return tempCards;
    }

    private Card getFromTable(MoveContext context, SelectCardOptions sco) {
        return getFromTable(context, null, sco);
    }

    private Card getFromTable(MoveContext context, String header, SelectCardOptions sco) {
        sco.fromTable();
        Card[] cards = context.getCardsInGame();

        for (Card card : cards) {
            if (sco.allowEmpty || !context.game.isPileEmpty(card)) {
                if (sco.checkValid(card, card.getCost(context))) {
                    sco.addValidCard(cardToInt(card));
                }
            }
        }

        if (sco.getAllowedCardCount() == 0) {
            // No cards fit the filter, so return early
            return null;
        }
        else if (sco.getAllowedCardCount() == 1 && !sco.isPassable()) {
            // Only one card available and player can't pass...go ahead and return
            return intToCard(sco.allowedCards.get(0));
        }
        return pickACard(context, header, sco);
    }

    public int selectInt(MoveContext context, String header, int maxInt, int errVal) {
        ArrayList<String> options = new ArrayList<String>();
        for (int i=0; i<=maxInt; i++)
            options.add("" + i);

        String o = selectString(context, header, options.toArray(new String[0]));

        try {
            return Integer.parseInt(o);
        } catch (NumberFormatException e) {
            return errVal;
        }
    }
    public boolean selectBoolean(MoveContext context, Card c, String strTrue, String strFalse) {
        String header = getCardName(c);
        return selectBoolean(context, header, strTrue, strFalse);
    }

    public boolean selectBoolean(MoveContext context, String header, String strTrue, String strFalse) {
        String [] s = new String [] {strTrue, strFalse};
        String r = selectString(context, header, s);
        if (strTrue.equals(r))
            return true;
        return false;
    }
    public boolean selectBooleanWithCard(MoveContext context, String header, Card c, String strTrue, String strFalse) {
        return selectBoolean(context, header + Strings.getCardName(c), strTrue, strFalse);
    }
    public boolean selectBooleanCardRevealed(MoveContext context, Card cardResponsible, Card cardRevealed, String strTrue, String strFalse) {
        String c1 = getCardName(cardResponsible);
        String c2 = getCardName(cardRevealed);
        String query = Strings.format(R.string.card_revealed, c1, c2);
        return selectBoolean(context, query, strTrue, strFalse);
    }

    public boolean selectBooleanCardRevealedFromHand(MoveContext context, Card cardResponsible, Card cardRevealed, String strTrue, String strFalse) {
        String c1 = getCardName(cardResponsible);
        String c2 = getCardName(cardRevealed);
        String query = Strings.format(R.string.card_revealed_from_hand, c1, c2);
        return selectBoolean(context, query, strTrue, strFalse);
    }

    public boolean selectBooleanCardRevealedAndPlayer(MoveContext context, Card cardResponsible, Card cardRevealed, Player p, String strTrue, String strFalse) {
        String c1 = getCardName(cardResponsible);
        String c2 = getCardName(cardRevealed);
        String query = Strings.format(R.string.card_revealed_from_player, p.getPlayerName(), c1, c2);
        return selectBoolean(context, query, strTrue, strFalse);
    }

    @Override
    public Card[] topOfDeck_orderCards(MoveContext context, Card[] cards) {
        if (context.isQuickPlay() && shouldAutoPlay_topOfDeck_orderCards(context, cards)) {
            return super.topOfDeck_orderCards(context, cards);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards));
        for (int i : order)
            orderedCards.add(cards[i]);
        return orderedCards.toArray(new Card[0]);
    }

    private Card[] doAction(MoveContext context, boolean singleCard) {
        int actionCount = 0;
        Card actionCard = null;
        for (Card card : (context.player.isPossessed()) ? context.player.getHand() : getHand()) {
            if (card instanceof ActionCard) {
                actionCount++;
                actionCard = card;
            }
        }
        if (actionCount == 0)
            return null;

        SelectCardOptions sco = new SelectCardOptions().isAction().setPassable(getString(R.string.none));
        if (singleCard) 
            sco.setCount(1).setPickType(PickType.PLAY);
        else 
            sco.setCount(actionCount).ordered().setPickType(PickType.PLAY_IN_ORDER);

        Card[] cards = getFromHand(context, getString(R.string.part_play), sco);

        if (cards == null)
            return null;
        // Hack that tells us that "Play the only one card" was selected
        else if (actionCount == 1 && cards.length == 0) {
            cards = new Card[1];
            cards[0] = actionCard;
        }
        return cards;
    }

    @Override
    public Card doAction(MoveContext context) {
        Card[] cards = doAction(context, true);
        return (cards == null ? null : cards[0]); 
    }

    @Override
    public Card[] actionCardsToPlayInOrder(MoveContext context) {
        return doAction(context, false);
    }

    @Override
    public Card doBuy(MoveContext context) {
        SelectCardOptions sco = new SelectCardOptions().isBuy().maxCost(context.getCoinAvailableForBuy()).copperCountInPlay(context.countCardsInPlay(Cards.copper)).potionCost(context.getPotions()).setPassable(getString(R.string.end_turn)).setPickType(PickType.BUY);
        return getFromTable(context, getString(R.string.part_buy), sco);
    }

    @Override
    public ArrayList<TreasureCard> treasureCardsToPlayInOrder(MoveContext context) {
        if(context.isQuickPlay()) {
            return super.treasureCardsToPlayInOrder(context);
        }

        int treasureCount = 0;
        for (Card card : (context.player.isPossessed()) ? context.player.getHand() : getHand()) {
            if (card instanceof TreasureCard) {
                treasureCount++;
            }
        }

        SelectCardOptions sco = new SelectCardOptions().isTreasure().setPassable(getString(R.string.none)).setCount(treasureCount).ordered().setPickType(PickType.SELECT_WITH_ALL);
        Card[] cards = getFromHand(context, getString(R.string.use_for_money), sco);
        if (cards == null) {
            return null;
        }

        // Hack that tells us that "All" was selected
        if(cards.length == 0) {
            return super.treasureCardsToPlayInOrder(context);
        }

        ArrayList<TreasureCard> treasures = new ArrayList<TreasureCard>();
        for (int i = 0; i < cards.length; i++) {
            treasures.add((TreasureCard) cards[i]);
        }
        return treasures;
    }

    // ////////////////////////////////////////////
    // Card interactions - cards from the base game
    // ////////////////////////////////////////////
    @Override
    public Card workshop_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_workshop_cardToObtain(context)) {
            return super.workshop_cardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().maxCost(4).potionCost(0)
                .setCardResponsible(Cards.workshop).setActionType(ActionType.GAIN);
        return getFromTable(context, sco);
    }

    @Override
    public Card feast_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_feast_cardToObtain(context)) {
            return super.feast_cardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(5)
                .setCardResponsible(Cards.feast).setActionType(ActionType.GAIN);
        return getFromTable(context, sco);
    }

    @Override
    public Card remodel_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_remodel_cardToTrash(context)) {
            return super.remodel_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.UPGRADE)
                .setCardResponsible(Cards.remodel).setActionType(ActionType.TRASH);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card remodel_cardToObtain(MoveContext context, int maxCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_remodel_cardToObtain(context, maxCost, potion)) {
            return super.remodel_cardToObtain(context, maxCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().maxCost(maxCost).potionCost(potion ? 1 : 0)
                .setCardResponsible(Cards.remodel).setActionType(ActionType.GAIN);
        return getFromTable(context, sco);
    }

    @Override
    public TreasureCard mine_treasureToObtain(MoveContext context, int maxCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_mine_treasureToObtain(context, maxCost, potion)) {
            return super.mine_treasureToObtain(context, maxCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().isTreasure().maxCost(maxCost)
                .potionCost(potion ? 1 : 0).setCardResponsible(Cards.mine);
        return (TreasureCard) getFromTable(context, sco);
    }

    @Override
    public Card[] militia_attack_cardsToKeep(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_militia_attack_cardsToKeep(context)) {
            return super.militia_attack_cardsToKeep(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(3).exactCount()
                .setPickType(PickType.KEEP).setCardResponsible(Cards.militia);
        return getFromHand(context, sco);
    }

    @Override
    public boolean chancellor_shouldDiscardDeck(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_chancellor_shouldDiscardDeck(context)) {
            return super.chancellor_shouldDiscardDeck(context);
        }
        return selectBoolean(context, getCardName(Cards.chancellor), getString(R.string.chancellor_query), getString(R.string.pass));
    }

    @Override
    public TreasureCard mine_treasureFromHandToUpgrade(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_mine_treasureFromHandToUpgrade(context)) {
            return super.mine_treasureFromHandToUpgrade(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isTreasure().setPickType(PickType.UPGRADE)
                .setCardResponsible(Cards.mine);
        return (TreasureCard) getCardFromHand(context, sco);
    }

    @Override
    public Card[] chapel_cardsToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_chapel_cardsToTrash(context)) {
            return super.chapel_cardsToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(4)
                .setPassable(getString(R.string.none)).setPickType(PickType.TRASH)
                .setCardResponsible(Cards.chapel).setActionType(ActionType.TRASH);
        return getFromHand(context, sco);
    }

    @Override
    public Card[] cellar_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_cellar_cardsToDiscard(context)) {
            return super.cellar_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.cellar);
        return getFromHand(context, sco);
    }

    @Override
    public boolean library_shouldKeepAction(MoveContext context, ActionCard action) {
        if(context.isQuickPlay() && shouldAutoPlay_library_shouldKeepAction(context, action)) {
            return super.library_shouldKeepAction(context, action);
        }
        return selectBooleanCardRevealed(context, Cards.library, action, getString(R.string.keep), getString(R.string.discard));
    }

    @Override
    public boolean spy_shouldDiscard(MoveContext context, Player targetPlayer, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_spy_shouldDiscard(context, targetPlayer, card)) {
            return super.spy_shouldDiscard(context, targetPlayer, card);
        }
        return selectBooleanCardRevealedAndPlayer(context, Cards.spy, card, targetPlayer, getString(R.string.discard), getString(R.string.replace));
    }

    // ////////////////////////////////////////////
    // Card interactions - cards from the Intrigue
    // ////////////////////////////////////////////
    @Override
    public Card[] secretChamber_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_secretChamber_cardsToDiscard(context)) {
            return super.secretChamber_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.secretChamber);
        return getFromHand(context, sco);
    }

    @Override
    public PawnOption[] pawn_chooseOptions(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_pawn_chooseOptions(context)) {
            return super.pawn_chooseOptions(context);
        }
        // There's probably some code that could be shared between this and the Trusty Steed
        // method, though it would be better if there were some Option superclass that I could use
        // instead of Object.
        PawnOption[] choices = new PawnOption[2];

        PawnOption[] options = PawnOption.values();
        int choiceOne = selectOption(context, Cards.pawn, options);
        choices[0] = options[choiceOne];
        PawnOption[] secondOptions = new PawnOption[options.length - 1];
        int j = 0;
        for (int i=0; i<options.length; i++, j++) {
            if (i == choiceOne) {
                i++;
            }
            secondOptions[j] = options[i];
        }
        int choiceTwo = selectOption(context, Cards.pawn, secondOptions);
        choices[1] = secondOptions[choiceTwo];
        return choices;
    }

    @Override
    public SpiceMerchantOption spiceMerchant_chooseOption(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_spiceMerchant_chooseOption(context)) {
            return super.spiceMerchant_chooseOption(context);
        }
        SpiceMerchantOption[] options = SpiceMerchantOption.values();
        return options[selectOption(context, Cards.spiceMerchant, options)];
    }

    @Override
    public TorturerOption torturer_attack_chooseOption(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_torturer_attack_chooseOption(context)) {
            return super.torturer_attack_chooseOption(context);
        }
        TorturerOption[] options = TorturerOption.values();
        return options[selectOption(context, Cards.torturer, options)];
    }

    @Override
    public StewardOption steward_chooseOption(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_steward_chooseOption(context)) {
            return super.steward_chooseOption(context);
        }
        StewardOption[] options = StewardOption.values();
        return options[selectOption(context, Cards.steward, options)];
    }

    @Override
    public Card swindler_cardToSwitch(MoveContext context, int cost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_swindler_cardToSwitch(context, cost, potion)) {
            return super.swindler_cardToSwitch(context, cost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().exactCost(cost).potionCost(potion ? 1 : 0)
                .setCardResponsible(Cards.swindler);
        return getFromTable(context, sco);
    }

    @Override
    public Card[] steward_cardsToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_steward_cardsToTrash(context)) {
            return super.steward_cardsToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount()
                .setPickType(PickType.TRASH).setActionType(ActionType.TRASH)
                .setCardResponsible(Cards.steward);
        return getFromHand(context, sco);
    }

    @Override
    public Card[] torturer_attack_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_torturer_attack_cardsToDiscard(context)) {
            return super.torturer_attack_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount()
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.torturer);
        return getFromHand(context, sco);
    }

    @Override
    public Card courtyard_cardToPutBackOnDeck(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_courtyard_cardToPutBackOnDeck(context)) {
            return super.courtyard_cardToPutBackOnDeck(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCardResponsible(Cards.courtyard);
        return getCardFromHand(context, sco);
    }

    @Override
    public boolean baron_shouldDiscardEstate(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_baron_shouldDiscardEstate(context)) {
            return super.baron_shouldDiscardEstate(context);
        }
        return selectBoolean(context, getCardName(Cards.baron), getString(R.string.baron_option_one), getString(R.string.baron_option_two));
    }

    @Override
    public Card ironworks_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_ironworks_cardToObtain(context)) {
            return super.ironworks_cardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(4)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.ironworks);
        return getFromTable(context, sco);
    }

    @Override
    public Card masquerade_cardToPass(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_masquerade_cardToPass(context)) {
            return super.masquerade_cardToPass(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.GIVE)
                .setCardResponsible(Cards.masquerade);
        return getCardFromHand(context, sco);
    }

    @Override
    public VictoryCard bureaucrat_cardToReplace(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_bureaucrat_cardToReplace(context)) {
            return super.bureaucrat_cardToReplace(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isVictory()
                .setCardResponsible(Cards.bureaucrat);
        return (VictoryCard) getCardFromHand(context, sco);
    }

    @Override
    public Card masquerade_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_masquerade_cardToTrash(context)) {
            return super.masquerade_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .setPickType(PickType.TRASH).setActionType(ActionType.TRASH)
                .setCardResponsible(Cards.masquerade);
        return getCardFromHand(context, sco);
    }

    @Override
    public boolean miningVillage_shouldTrashMiningVillage(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_miningVillage_shouldTrashMiningVillage(context)) {
            return super.miningVillage_shouldTrashMiningVillage(context);
        }
        return selectBoolean(context, getCardName(Cards.miningVillage), getString(R.string.mining_village_option_one), getString(R.string.keep));
    }

    @Override
    public Card saboteur_cardToObtain(MoveContext context, int maxCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_saboteur_cardToObtain(context, maxCost, potion)) {
            return super.saboteur_cardToObtain(context, maxCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .maxCost(maxCost).potionCost(potion ? 1 : 0).setCardResponsible(Cards.saboteur);
        return getFromTable(context, sco);
    }

    @Override
    public Card[] scout_orderCards(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_scout_orderCards(context, cards)) {
            return super.scout_orderCards(context, cards);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards));
        for (int i : order)
            orderedCards.add(cards[i]);
        return orderedCards.toArray(new Card[0]);
    }

    @Override
    public NoblesOption nobles_chooseOptions(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_nobles_chooseOptions(context)) {
            return super.nobles_chooseOptions(context);
        }
        NoblesOption[] options = NoblesOption.values();
        return options[selectOption(context, Cards.nobles, options)];
    }

    // Either return two cards, or null if you do not want to trash any cards.
    @Override
    public Card[] tradingPost_cardsToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_tradingPost_cardsToTrash(context)) {
            return super.tradingPost_cardsToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount()
                .setPickType(PickType.TRASH).setActionType(ActionType.TRASH)
                .setCardResponsible(Cards.tradingPost);
        return getFromHand(context, sco);
    }

    @Override
    public Card wishingWell_cardGuess(MoveContext context, ArrayList<Card> cardList) {
        if(context.isQuickPlay() && shouldAutoPlay_wishingWell_cardGuess(context)) {
            return super.wishingWell_cardGuess(context, cardList);
        }

        LinkedHashMap<String, Card> h = new LinkedHashMap<String, Card>();

        // Add option to skip the guess
        h.put("None", null);

        for (Card c : cardList) {
            h.put(c.getName(), c);
        }

        String choice = selectString(context, getActionString(ActionType.NAMECARD, Cards.wishingWell), h.keySet().toArray(new String[0])); 

        return h.get(choice);
    }

    @Override
    public Card upgrade_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_upgrade_cardToTrash(context)) {
            return super.upgrade_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.upgrade);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card upgrade_cardToObtain(MoveContext context, int exactCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_upgrade_cardToObtain(context, exactCost, potion)) {
            return super.upgrade_cardToObtain(context, exactCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().exactCost(exactCost)
                .potionCost(potion ? 1 : 0).setActionType(ActionType.GAIN)
                .setCardResponsible(Cards.upgrade);
        return getFromTable(context, sco);
    }

    @Override
    public MinionOption minion_chooseOption(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_minion_chooseOption(context)) {
            return super.minion_chooseOption(context);
        }
        MinionOption[] options = MinionOption.values();
        return options[selectOption(context, Cards.minion, options)];
    }

    @Override
    public Card[] secretChamber_cardsToPutOnDeck(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_secretChamber_cardsToPutOnDeck(context)) {
            return super.secretChamber_cardsToPutOnDeck(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount().ordered()
                .setCardResponsible(Cards.secretChamber);
        return getFromHand(context, sco);
    }

    // ////////////////////////////////////////////
    // Card interactions - cards from the Seaside
    // ////////////////////////////////////////////
    @Override
    public Card[] ghostShip_attack_cardsToPutBackOnDeck(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_ghostShip_attack_cardsToPutBackOnDeck(context)) {
            return super.ghostShip_attack_cardsToPutBackOnDeck(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(-3).ordered()
                .setCardResponsible(Cards.ghostShip);
        return getFromHand(context, sco);
    }

    @Override
    public Card salvager_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_salvager_cardToTrash(context)) {
            return super.salvager_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.salvager);
        return getCardFromHand(context, sco);
    }

    @Override 
    public Card[] warehouse_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_warehouse_cardsToDiscard(context)) {
            return super.warehouse_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(3).exactCount()
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.warehouse);
        return getFromHand(context, sco);
    }

    @Override
    public boolean pirateShip_takeTreasure(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_pirateShip_takeTreasure(context)) {
            return super.pirateShip_takeTreasure(context);
        }
        int t = this.getPirateShipTreasure();
        return selectBoolean(context, getCardName(Cards.pirateShip), Strings.format(R.string.pirate_ship_option_one, "" + t), getString(R.string.pirate_ship_option_two));
    }

    @Override
    public boolean nativeVillage_takeCards(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_nativeVillage_takeCards(context)) {
            return super.nativeVillage_takeCards(context);
        }
        return selectBoolean(context, getCardName(Cards.nativeVillage), getString(R.string.native_village_option_one), getString(R.string.native_village_option_two));
    }

    @Override
    public Card smugglers_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_smugglers_cardToObtain(context)) {
            return super.smugglers_cardToObtain(context);
        }
        ArrayList<String> options = new ArrayList<String>();
        Card[] cards = context.getCardsObtainedByLastPlayer().toArray(new Card[0]);
        for (Card c : cards)
            if (c.getCost(context) <= 6 && !c.isPrize())
                options.add(Strings.getCardName(c));

        if (options.size() > 0) {
            String o = selectString(context, getString(R.string.smuggle_query), options.toArray(new String[0]));
            return localNameToCard(o, cards);
        } else
            return null;
    }

    @Override
    public Card island_cardToSetAside(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_island_cardToSetAside(context)) {
            return super.island_cardToSetAside(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCardResponsible(Cards.island);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card haven_cardToSetAside(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_haven_cardToSetAside(context)) {
            return super.haven_cardToSetAside(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCardResponsible(Cards.haven);
        return getCardFromHand(context, sco);
    }

    @Override
    public boolean navigator_shouldDiscardTopCards(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_navigator_shouldDiscardTopCards(context, cards)) {
            return super.navigator_shouldDiscardTopCards(context, cards);
        }
        String header = "";
        for (Card c : cards)
            header += getCardName(c) + ", ";
        header += "--";
        header = header.replace(", --", "");
        header = Strings.format(R.string.navigator_header, header);

        String option1 = getString(R.string.discard);
        String option2 = getString(R.string.navigator_option_two);

        return selectBoolean(context, header, option1, option2);
    }

    @Override
    public Card[] navigator_cardOrder(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_navigator_cardOrder(context, cards)) {
            return super.navigator_cardOrder(context, cards);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards));
        for (int i : order) {
            orderedCards.add(cards[i]);
        }
        return orderedCards.toArray(new Card[0]);
    }

    @Override
    public Card embargo_supplyToEmbargo(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_embargo_supplyToEmbargo(context)) {
            return super.embargo_supplyToEmbargo(context);
        }
        SelectCardOptions sco = new SelectCardOptions().allowEmpty()
                .setCardResponsible(Cards.embargo);
        return getFromTable(context, sco);
    }

    // Will be passed all three cards
    @Override
    public Card lookout_cardToTrash(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_lookout_cardToTrash(context, cards)) {
            return super.lookout_cardToTrash(context, cards);
        }
        ArrayList<String> options = new ArrayList<String>();
        for (Card c : cards)
            options.add(Strings.getCardName(c));

        String o = selectString(context, R.string.lookout_query_trash, Cards.lookout, options.toArray(new String[0]));
        return localNameToCard(o, cards);
    }

    // Will be passed the two cards leftover after trashing one
    @Override
    public Card lookout_cardToDiscard(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_lookout_cardToDiscard(context, cards)) {
            return super.lookout_cardToDiscard(context, cards);
        }
        ArrayList<String> options = new ArrayList<String>();
        for (Card c : cards)
            options.add(Strings.getCardName(c));

        String o = selectString(context, R.string.lookout_query_discard, Cards.lookout, options.toArray(new String[0]));
        return localNameToCard(o, cards);
    }

    @Override
    public Card ambassador_revealedCard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_ambassador_revealedCard(context)) {
            return super.ambassador_revealedCard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setActionType(ActionType.REVEAL)
                .setCardResponsible(Cards.ambassador);
        return getCardFromHand(context, sco);
    }

    @Override
    public int ambassador_returnToSupplyFromHand(MoveContext context, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_ambassador_returnToSupplyFromHand(context, card)) {
            return super.ambassador_returnToSupplyFromHand(context, card);
        }
        int numCards = 0;
        for (Card c : (context.player.isPossessed()) ? context.player.getHand() : getHand())
            if (c.equals(card))
                numCards++;

        return selectInt(context, Strings.format(R.string.ambassador_query, getCardName(card)), Math.min(2, numCards), 0);
    }

    @Override
    public boolean pearlDiver_shouldMoveToTop(MoveContext context, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_pearlDiver_shouldMoveToTop(context, card)) {
            return super.pearlDiver_shouldMoveToTop(context, card);
        }

        String option1 = getString(R.string.pearldiver_option_one);
        String option2 = getString(R.string.pearldiver_option_two);
        return selectBooleanCardRevealed(context, Cards.pearlDiver, card, option1, option2);
    }

    @Override
    public boolean explorer_shouldRevealProvince(MoveContext context) {
        if (context.isQuickPlay() && shouldAutoPlay_explorer_shouldRevealProvince(context)) {
            super.explorer_shouldRevealProvince(context);
        }
        return selectBoolean(context, Cards.explorer, Strings.getString(R.string.explorer_reveal), Strings.getString(R.string.pass));
    }

    @Override
    public Card transmute_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_transmute_cardToTrash(context)) {
            return super.transmute_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.transmute);
        return getCardFromHand(context, sco);
    }

    @Override
    public ArrayList<Card> apothecary_cardsForDeck(MoveContext context, ArrayList<Card> cards) {
        if(context.isQuickPlay() && shouldAutoPlay_apothecary_cardsForDeck(context, cards)) {
            return super.apothecary_cardsForDeck(context, cards);
        }

        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards.toArray(new Card[0])));
        for (int i : order)
            orderedCards.add(cards.get(i));
        return orderedCards;
    }

    @Override
    public boolean alchemist_backOnDeck(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_alchemist_backOnDeck(context)) {
            return super.alchemist_backOnDeck(context);
        }
        String option1 = getString(R.string.alchemist_option_one);
        String option2 = getString(R.string.alchemist_option_two);
        return selectBoolean(context, Cards.alchemist, option1, option2);
    }

    @Override
    public TreasureCard herbalist_backOnDeck(MoveContext context, TreasureCard[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_herbalist_backOnDeck(context, cards)) {
            return super.herbalist_backOnDeck(context, cards);
        }
        ArrayList<String> options = new ArrayList<String>();
        for (Card c : cards)
            options.add(Strings.getCardName(c));

        //        String none = getString(R.string.none);
        //        options.add(none);
        String o = selectString(context, R.string.herbalist_query, Cards.herbalist, options.toArray(new String[0]));
        //        if(o.equals(none)) {
        //            return null;
        //        }
        return (TreasureCard) localNameToCard(o, cards);
    }

    @Override
    public Card apprentice_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_apprentice_cardToTrash(context)) {
            return super.apprentice_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.apprentice);
        return getCardFromHand(context, sco);
    }

    @Override
    public ActionCard university_actionCardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_university_actionCardToObtain(context)) {
            return super.university_actionCardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(5).isAction()
                .setPassable(getString(R.string.none)).setCardResponsible(Cards.university);
        return (ActionCard) getFromTable(context, sco);
    }

    @Override
    public boolean scryingPool_shouldDiscard(MoveContext context, Player targetPlayer, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_scryingPool_shouldDiscard(context, targetPlayer, card)) {
            return super.scryingPool_shouldDiscard(context, targetPlayer, card);
        }
        return selectBooleanCardRevealedAndPlayer(context, Cards.scryingPool, card, targetPlayer, getString(R.string.discard), getString(R.string.replace));
    }

    @Override
    public ActionCard[] golem_cardOrder(MoveContext context, ActionCard[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_golem_cardOrder(context, cards)) {
            return super.golem_cardOrder(context, cards);
        }

        if(cards == null || cards.length < 2) {
            return cards;
        }

        ArrayList<String> options = new ArrayList<String>();
        for (Card c : cards)
            options.add(Strings.getCardName(c));

        String o = selectString(context, R.string.golem_first_action, Cards.golem, options.toArray(new String[0]));
        Card c = localNameToCard(o, cards);
        if(c.equals(cards[0])) {
            return cards;
        }
        return new ActionCard[]{ cards[1], cards[0] };
    }

    @Override
    public Card bishop_cardToTrashForVictoryTokens(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_bishop_cardToTrashForVictoryTokens(context)) {
            return super.bishop_cardToTrashForVictoryTokens(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setCardResponsible(Cards.bishop);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card bishop_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_bishop_cardToTrash(context)) {
            return super.bishop_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .setPickType(PickType.TRASH).setActionType(ActionType.TRASH)
                .setCardResponsible(Cards.bishop);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card contraband_cardPlayerCantBuy(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_contraband_cardPlayerCantBuy(context)) {
            return super.contraband_cardPlayerCantBuy(context);
        }
        SelectCardOptions sco = new SelectCardOptions().allowEmpty()
                .setCardResponsible(Cards.contraband);
        return getFromTable(context, sco);
    }

    @Override
    public Card expand_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_expand_cardToTrash(context)) {
            return super.expand_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.expand);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card expand_cardToObtain(MoveContext context, int maxCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_expand_cardToObtain(context, maxCost, potion)) {
            return super.expand_cardToObtain(context, maxCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().maxCost(maxCost).potionCost(potion ? 1 : 0)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.expand);
        return getFromTable(context, sco);
    }

    @Override
    public Card[] forge_cardsToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_forge_cardsToTrash(context)) {
            return super.forge_cardsToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .setPickType(PickType.TRASH).setActionType(ActionType.TRASH)
                .setCardResponsible(Cards.forge);
        return getFromHand(context, sco);
    }

    @Override
    public Card forge_cardToObtain(MoveContext context, int exactCost) {
        if(context.isQuickPlay() && shouldAutoPlay_forge_cardToObtain(context, exactCost)) {
            return super.forge_cardToObtain(context, exactCost);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).exactCost(exactCost)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.forge);
        return getFromTable(context, sco);
    }

    @Override
    public Card[] goons_attack_cardsToKeep(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_goons_attack_cardsToKeep(context)) {
            return super.goons_attack_cardsToKeep(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(3).exactCount()
                .setPickType(PickType.KEEP).setCardResponsible(Cards.goons);
        return getFromHand(context, sco);
    }

    @Override
    public ActionCard kingsCourt_cardToPlay(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_kingsCourt_cardToPlay(context)) {
            return super.kingsCourt_cardToPlay(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isAction()
                .setPassable(getString(R.string.none)).setPickType(PickType.PLAY)
                .setCardResponsible(Cards.kingsCourt);
        return (ActionCard) getCardFromHand(context, sco);
    }

    @Override
    public ActionCard throneRoom_cardToPlay(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_throneRoom_cardToPlay(context)) {
            return super.throneRoom_cardToPlay(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isAction()
                .setPassable(getString(R.string.none)).setPickType(PickType.PLAY)
                .setCardResponsible(Cards.throneRoom);
        return (ActionCard) getCardFromHand(context, sco);
    }

    @Override
    public boolean loan_shouldTrashTreasure(MoveContext context, TreasureCard treasure) {
        if(context.isQuickPlay() && shouldAutoPlay_loan_shouldTrashTreasure(context, treasure)) {
            return super.loan_shouldTrashTreasure(context, treasure);
        }
        return selectBooleanCardRevealed(context, Cards.loan, treasure, getString(R.string.trash), getString(R.string.discard));
    }

    @Override
    public TreasureCard mint_treasureToMint(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_mint_treasureToMint(context)) {
            return super.mint_treasureToMint(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isTreasure()
                .setPassable(getString(R.string.pass)).setPickType(PickType.MINT)
                .setCardResponsible(Cards.mint);
        return (TreasureCard) getCardFromHand(context, sco);
    }

    @Override
    public boolean mountebank_attack_shouldDiscardCurse(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_mountebank_attack_shouldDiscardCurse(context)) {
            return super.mountebank_attack_shouldDiscardCurse(context);
        }
        return selectBoolean(context, getString(R.string.mountebank_query), getString(R.string.mountebank_option_one), getString(R.string.mountebank_option_two));
    }

    @Override
    public Card[] rabble_attack_cardOrder(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_rabble_attack_cardOrder(context, cards)) {
            return super.rabble_attack_cardOrder(context, cards);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards));
        for (int i : order)
            orderedCards.add(cards[i]);
        return orderedCards.toArray(new Card[0]);
    }

    @Override
    public boolean royalSeal_shouldPutCardOnDeck(MoveContext context, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_royalSeal_shouldPutCardOnDeck(context, card)) {
            return super.royalSeal_shouldPutCardOnDeck(context, card);
        }
        return selectBooleanCardRevealed(context, Cards.royalSeal, card, getString(R.string.top_of_deck), getString(R.string.take_normal));
    }

    @Override
    public Card tradeRoute_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_tradeRoute_cardToTrash(context)) {
            return super.tradeRoute_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.tradeRoute);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card[] vault_cardsToDiscardForGold(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_vault_cardsToDiscardForGold(context)) {
            return super.vault_cardsToDiscardForGold(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .setPickType(PickType.DISCARD).setCardResponsible(Cards.vault);
        return getFromHand(context, sco);
    }

    @Override
    public Card[] vault_cardsToDiscardForCard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_vault_cardsToDiscardForCard(context)) {
            return super.vault_cardsToDiscardForCard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount()
                .setPassable(getString(R.string.none)).setPickType(PickType.DISCARD)
                .setCardResponsible(Cards.vault);
        return getFromHand(context, sco);
    }

    @Override
    public WatchTowerOption watchTower_chooseOption(MoveContext context, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_watchTower_chooseOption(context, card)) {
            return super.watchTower_chooseOption(context, card);
        }
        WatchTowerOption[] options = WatchTowerOption.values();
        return options[selectOption(context, Cards.watchTower, options)];
    }

    @Override
    public Card hamlet_cardToDiscardForAction(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_hamlet_cardToDiscardForAction(context)) {
            return super.hamlet_cardToDiscardForAction(context);
        }
        // WARNING: This is a total hack!  We need to differentiate the "discard for action" from
        // the "discard for buy", but we don't have any way in the SelectCardOptions to do that.
        // So we set a fake action type here, and handle this as a special case in Strings.java.
        // This is fragile and could easily break if the rest of the code changes and we aren't
        // careful.  TODO(matt): come up with a better way to do this.
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.hamlet);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card hamlet_cardToDiscardForBuy(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_hamlet_cardToDiscardForBuy(context)) {
            return super.hamlet_cardToDiscardForBuy(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none))
                .setPickType(PickType.DISCARD).setCardResponsible(Cards.hamlet);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card hornOfPlenty_cardToObtain(MoveContext context, int maxCost) {
        if(context.isQuickPlay() && shouldAutoPlay_hornOfPlenty_cardToObtain(context, maxCost)) {
            return super.hornOfPlenty_cardToObtain(context, maxCost);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(maxCost)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.hornOfPlenty);
        return getFromTable(context, sco);
    }

    @Override
    public Card[] horseTraders_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_horseTraders_cardsToDiscard(context)) {
            return super.horseTraders_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount()
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.horseTraders);
        return getFromHand(context, sco);
    }

    @Override
    public JesterOption jester_chooseOption(MoveContext context, Player targetPlayer, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_jester_chooseOption(context, targetPlayer, card)) {
            return super.jester_chooseOption(context, targetPlayer, card);
        }
        /*
         * TODO(matt): this one needs a little more work, because we need to pass in the player,
         * too.
        JesterOption[] options = JesterOption.values();
        return options[selectOption(context, Cards.jester, options)];
        */

        LinkedHashMap<String, JesterOption> h = new LinkedHashMap<String, JesterOption>();

        h.put(getString(R.string.jester_option_one), JesterOption.GainCopy);
        h.put(Strings.format(R.string.jester_option_two, targetPlayer.getPlayerName()), JesterOption.GiveCopy);

        String header = Strings.format(R.string.card_revealed, getCardName(Cards.jester), getCardName(card));
        return h.get(selectString(context, header, h.keySet().toArray(new String[0])));
    }

    @Override
    public Card remake_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_remake_cardToTrash(context)) {
            return super.remake_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.remake);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card remake_cardToObtain(MoveContext context, int exactCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_remake_cardToObtain(context, exactCost, potion)) {
            return super.remake_cardToObtain(context, exactCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().exactCost(exactCost)
                .potionCost(potion ? 1 : 0).setActionType(ActionType.GAIN)
                .setCardResponsible(Cards.remake);
        return getFromTable(context, sco);
    }

    @Override
    public boolean tournament_shouldRevealProvince(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_tournament_shouldRevealProvince(context)) {
            return super.tournament_shouldRevealProvince(context);
        }
        return selectBoolean(context,
                             Cards.tournament,
                             Strings.getString(R.string.tournament_reveal),
                             Strings.getString(R.string.tournament_option_one));
    }

    @Override
    public TournamentOption tournament_chooseOption(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_tournament_chooseOption(context)) {
            return super.tournament_chooseOption(context);
        }
        TournamentOption[] options = TournamentOption.values();
        return options[selectOption(context, Cards.tournament, options)];
    }

    @Override
    public Card tournament_choosePrize(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_tournament_choosePrize(context)) {
            return super.tournament_choosePrize(context);
        }
        SelectCardOptions sco = new SelectCardOptions().fromPrizes()
                .setCardResponsible(Cards.tournament);
        return getFromTable(context, sco);
    }

    @Override
    public Card[] youngWitch_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_youngWitch_cardsToDiscard(context)) {
            return super.youngWitch_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount()
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.youngWitch);
        return getFromHand(context, sco);
    }

    @Override
    public Card[] followers_attack_cardsToKeep(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_followers_attack_cardsToKeep(context)) {
            return super.followers_attack_cardsToKeep(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(3).exactCount()
                .setPickType(PickType.KEEP).setCardResponsible(Cards.followers);
        return getFromHand(context, sco);
    }

    @Override
    public TrustySteedOption[] trustySteed_chooseOptions(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_trustySteed_chooseOptions(context)) {
            return super.trustySteed_chooseOptions(context);
        }
        TrustySteedOption[] choices = new TrustySteedOption[2];

        TrustySteedOption[] options = TrustySteedOption.values();
        int choiceOne = selectOption(context, Cards.trustySteed, options);
        choices[0] = options[choiceOne];
        TrustySteedOption[] secondOptions = new TrustySteedOption[options.length - 1];
        int j = 0;
        for (int i=0; i<options.length; i++, j++) {
            if (i == choiceOne) {
                i++;
            }
            secondOptions[j] = options[i];
        }
        int choiceTwo = selectOption(context, Cards.trustySteed, secondOptions);
        choices[1] = secondOptions[choiceTwo];
        return choices;
    }

    @Override
    public TreasureCard thief_treasureToTrash(MoveContext context, TreasureCard[] treasures) {
        if(context.isQuickPlay() && shouldAutoPlay_thief_treasureToTrash(context, treasures)) {
            return super.thief_treasureToTrash(context, treasures);
        }
        ArrayList<String> options = new ArrayList<String>();
        for (TreasureCard c : treasures)
            options.add(Strings.getCardName(c));

        if (options.size() > 0) {
            String o = selectString(context, R.string.treasure_to_trash, Cards.thief, options.toArray(new String[0]));
            return (TreasureCard) localNameToCard(o, treasures);
        } else {
            return null;
        }
    }

    @Override
    public TreasureCard[] thief_treasuresToGain(MoveContext context, TreasureCard[] treasures) {
        if(context.isQuickPlay() && shouldAutoPlay_thief_treasuresToGain(context, treasures)) {
            return super.thief_treasuresToGain(context, treasures);
        }
        ArrayList<String> options = new ArrayList<String>();
        options.add(getString(R.string.none));
        for (TreasureCard c : treasures)
            options.add(Strings.getCardName(c));

        if (options.size() > 0) {
            ArrayList<TreasureCard> toGain = new ArrayList<TreasureCard>();
            String o = null;

            while (options.size() > 1 && !getString(R.string.none).equals(o = selectString(context, R.string.thief_query, Cards.thief, options.toArray(new String[0])))) {
                toGain.add((TreasureCard) localNameToCard(o, treasures));
                options.remove(o);
            }

            return toGain.toArray(new TreasureCard[0]);
        } else {
            return null;
        }
    }

    @Override
    public TreasureCard pirateShip_treasureToTrash(MoveContext context, TreasureCard[] treasures) {
        if(context.isQuickPlay() && shouldAutoPlay_pirateShip_treasureToTrash(context, treasures)) {
            return super.pirateShip_treasureToTrash(context, treasures);
        }
        ArrayList<String> options = new ArrayList<String>();
        for (TreasureCard c : treasures)
            options.add(Strings.getCardName(c));

        if (options.size() > 0) {
            String o = selectString(context, R.string.treasure_to_trash, Cards.pirateShip, options.toArray(new String[0]));
            return (TreasureCard) localNameToCard(o, treasures);
        } else {
            return null;
        }
    }

    @Override
    public boolean tunnel_shouldReveal(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_tunnel_shouldReveal(context)) {
            return super.tunnel_shouldReveal(context);
        }
        return selectBoolean(context, getString(R.string.tunnel_query), getString(R.string.tunnel_option_one), getString(R.string.pass));
    }

    @Override
    public boolean duchess_shouldGainBecauseOfDuchy(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_duchess_shouldGainBecauseOfDuchy(context)) {
            return super.duchess_shouldGainBecauseOfDuchy(context);
        }
        return selectBoolean(context, getString(R.string.duchess_query), getString(R.string.duchess_option_one), getString(R.string.pass));
    }

    @Override
    public boolean duchess_shouldDiscardCardFromTopOfDeck(MoveContext context, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_duchess_shouldDiscardCardFromTopOfDeck(context, card)) {
            return super.duchess_shouldDiscardCardFromTopOfDeck(context, card);
        }
        return !selectBooleanCardRevealed(context, Cards.duchess, card, getString(R.string.duchess_play_option_one), getString(R.string.discard));
    }

    @Override
    public boolean foolsGold_shouldTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_foolsGold_shouldTrash(context)) {
            return super.foolsGold_shouldTrash(context);
        }
        return selectBoolean(context, Cards.foolsGold, getString(R.string.fools_gold_option_one), getString(R.string.pass));
    }

    @Override
    public boolean trader_shouldGainSilverInstead(MoveContext context, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_trader_shouldGainSilverInstead(context, card)) {
            return super.trader_shouldGainSilverInstead(context, card);
        }
        return !selectBoolean(context, Cards.trader, Strings.format(R.string.trader_gain, getCardName(card)), Strings.format(R.string.trader_gain_instead_of, getCardName(Cards.silver), getCardName(card)));
    }

    @Override
    public Card trader_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_trader_cardToTrash(context)) {
            return super.trader_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.trader);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card oasis_cardToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_oasis_cardToDiscard(context)) {
            return super.oasis_cardToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.DISCARD)
                .setActionType(ActionType.DISCARD).setCardResponsible(Cards.oasis);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card develop_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_develop_cardToTrash(context)) {
            return super.develop_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.develop);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card develop_lowCardToGain(MoveContext context, int cost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_develop_lowCardToGain(context, cost, potion)) {
            return super.develop_lowCardToGain(context, cost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().exactCost(cost).potionCost(potion ? 1 : 0)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.develop);
        return getFromTable(context, sco);
    }

    @Override
    public Card develop_highCardToGain(MoveContext context, int cost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_develop_highCardToGain(context, cost, potion)) {
            return super.develop_highCardToGain(context, cost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().exactCost(cost).potionCost(potion ? 1 : 0)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.develop);
        return getFromTable(context, sco);
    }

    @Override
    public Card[] develop_orderCards(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_develop_orderCards(context, cards)) {
            return super.develop_orderCards(context, cards);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards), getString(R.string.card_order_on_deck));
        for (int i : order)
            orderedCards.add(cards[i]);
        return orderedCards.toArray(new Card[0]);
    }

    @Override
    public TreasureCard nobleBrigand_silverOrGoldToTrash(MoveContext context, TreasureCard[] silverOrGoldCards) {
        if(context.isQuickPlay() && shouldAutoPlay_nobleBrigand_silverOrGoldToTrash(context, silverOrGoldCards)) {
            return super.nobleBrigand_silverOrGoldToTrash(context, silverOrGoldCards);
        }

        int highIndex;
        int lowIndex;
        if(silverOrGoldCards[0].getCost(context) >= silverOrGoldCards[1].getCost(context)) {
            highIndex = 0;
            lowIndex = 1;
        }
        else {
            highIndex = 1;
            lowIndex = 0;
        }

        if(selectBoolean(context, Strings.format(R.string.noble_brigand_query, context.getAttackedPlayer()), Strings.getCardName(silverOrGoldCards[lowIndex]), Strings.getCardName(silverOrGoldCards[highIndex]))) {
            return silverOrGoldCards[lowIndex];
        }
        else {
            return silverOrGoldCards[highIndex];
        }
    }

    @Override
    public boolean jackOfAllTrades_shouldDiscardCardFromTopOfDeck(MoveContext context, Card card) {
        if(context.isQuickPlay() && shouldAutoPlay_jackOfAllTrades_shouldDiscardCardFromTopOfDeck(context, card)) {
            super.jackOfAllTrades_shouldDiscardCardFromTopOfDeck(context, card);
        }
        return !selectBooleanCardRevealed(context, Cards.jackOfAllTrades, card, getString(R.string.jack_of_all_trades_option_one), getString(R.string.discard));
    }

    @Override
    public Card jackOfAllTrades_nonTreasureToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_jackOfAllTrades_nonTreasureToTrash(context)) {
            super.jackOfAllTrades_nonTreasureToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isNonTreasure()
                .setPassable(getString(R.string.none)).setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.jackOfAllTrades);
        return getCardFromHand(context, sco);
    }

    @Override
    public TreasureCard spiceMerchant_treasureToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_spiceMerchant_treasureToTrash(context)) {
            return super.spiceMerchant_treasureToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isTreasure()
                .setPassable(getString(R.string.none)).setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.spiceMerchant);
        return (TreasureCard) getCardFromHand(context, sco);
    }

    @Override
    public Card[] embassy_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_embassy_cardsToDiscard(context)) {
            return super.embassy_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(3).exactCount()
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.embassy);
        return getFromHand(context, sco);
    }

    @Override
    public Card[] cartographer_cardsFromTopOfDeckToDiscard(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_cartographer_cardsFromTopOfDeckToDiscard(context, cards)) {
            return super.cartographer_cardsFromTopOfDeckToDiscard(context, cards);
        }

        if(cards == null || cards.length == 0) {
            return cards;
        }

        ArrayList<Card> cardsToDiscard = new ArrayList<Card>();

        ArrayList<String> options = new ArrayList<String>();
        for (Card c : cards)
            options.add(Strings.getCardName(c));
        String none = getString(R.string.none);
        options.add(none);

        do {
            String o = selectString(context, R.string.Cartographer_query, Cards.cartographer, options.toArray(new String[0]));
            if (o.equals(none)) {
                break;
            }
            cardsToDiscard.add(localNameToCard(o, cards));
            options.remove(o);
        } while (options.size() > 1);

        return cardsToDiscard.toArray(new Card[0]);
    }

    @Override
    public Card[] cartographer_cardOrder(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_cartographer_cardOrder(context, cards)) {
            return super.cartographer_cardOrder(context, cards);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards));
        for (int i : order)
            orderedCards.add(cards[i]);
        return orderedCards.toArray(new Card[0]);
    }

    @Override
    public ActionCard scheme_actionToPutOnTopOfDeck(MoveContext context, ActionCard[] actions) {
        if(context.isQuickPlay() && shouldAutoPlay_scheme_actionToPutOnTopOfDeck(context, actions)) {
            return super.scheme_actionToPutOnTopOfDeck(context, actions);
        }
        ArrayList<String> options = new ArrayList<String>();
        for (ActionCard c : actions)
            options.add(Strings.getCardName(c));
        String none = getString(R.string.none);
        options.add(none);
        String o = selectString(context, R.string.scheme_query, Cards.scheme, options.toArray(new String[0]));
        if(o.equals(none)) {
            return null;
        }
        return (ActionCard) localNameToCard(o, actions);
    }

    @Override
    public boolean oracle_shouldDiscard(MoveContext context, Player player, ArrayList<Card> cards) {
        if(context.isQuickPlay() && shouldAutoPlay_oracle_shouldDiscard(context, player, cards)) {
            return super.oracle_shouldDiscard(context, player, cards);
        }
        String cardNames = "";
        boolean first = true;
        for(Card c : cards) {
            if(first)
                first = false;
            else
                cardNames += ", ";
            cardNames += Strings.getCardName(c);
        }
        String s = Strings.format(R.string.card_revealed, player.getPlayerName(), cardNames);
        return !selectBoolean(context, s, getString(R.string.top_of_deck), getString(R.string.discard));
    }

    @Override
    public Card[] oracle_orderCards(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_oracle_orderCards(context, cards)) {
            return super.oracle_orderCards(context, cards);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards));
        for (int i : order)
            orderedCards.add(cards[i]);
        return orderedCards.toArray(new Card[0]);
    }

    @Override
    public boolean illGottenGains_gainCopper(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_illGottenGains_gainCopper(context)) {
            return super.illGottenGains_gainCopper(context);
        }
        return selectBoolean(context, Cards.illGottenGains, getString(R.string.ill_gotten_gains_option_one), getString(R.string.pass));
    }

    @Override
    public Card haggler_cardToObtain(MoveContext context, int maxCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_haggler_cardToObtain(context, maxCost, potion)) {
            return super.haggler_cardToObtain(context, maxCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(potion?1:0)
                .maxCost(maxCost).maxCostWithoutPotion().isNonVictory()
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.haggler);
        return getFromTable(context, sco);
    }

    @Override
    public Card[] inn_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_inn_cardsToDiscard(context)) {
            return super.inn_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount()
                .setPickType(PickType.DISCARD).setActionType(ActionType.DISCARD)
                .setCardResponsible(Cards.inn);
        return getFromHand(context, sco);
    }

    @Override
    public boolean inn_shuffleCardBackIntoDeck(MoveContext context, ActionCard card) {
        if(context.isQuickPlay() && shouldAutoPlay_inn_shuffleCardBackIntoDeck(context, card)) {
            return super.inn_shuffleCardBackIntoDeck(context, card);
        }

        String option1 = getString(R.string.inn_option_one);
        String option2 = getString(R.string.inn_option_two);
        return selectBooleanCardRevealed(context, Cards.inn, card, option1, option2);
    }

    @Override
    public Card borderVillage_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_borderVillage_cardToObtain(context)) {
            return super.borderVillage_cardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0)
                .maxCost(Cards.borderVillage.getCost(context) - 1)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.borderVillage);
        return getFromTable(context, sco);
    }

    @Override
    public Card farmland_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_farmland_cardToTrash(context)) {
            return super.farmland_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.farmland);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card farmland_cardToObtain(MoveContext context, int exactCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_remodel_cardToObtain(context, exactCost, potion)) {
            return super.remodel_cardToObtain(context, exactCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().exactCost(exactCost)
                .potionCost(potion ? 1 : 0).setActionType(ActionType.GAIN)
                .setCardResponsible(Cards.farmland);
        return getFromTable(context, sco);
    }

    @Override
    public TreasureCard stables_treasureToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_stables_treasureToDiscard(context)) {
            return super.stables_treasureToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isTreasure()
                .setPassable(getString(R.string.none)).setPickType(PickType.DISCARD)
                .setActionType(ActionType.DISCARD).setCardResponsible(Cards.stables);
        return (TreasureCard) getCardFromHand(context, sco);
    }

    @Override
    public Card mandarin_cardToReplace(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_mandarin_cardToReplace(context)) {
            return super.mandarin_cardToReplace(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCardResponsible(Cards.mandarin);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card[] mandarin_orderCards(MoveContext context, Card[] cards) {
        if (context.isQuickPlay() && shouldAutoPlay_mandarin_orderCards(context, cards)) {
            return super.mandarin_orderCards(context, cards);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(cards));
        for (int i : order)
            orderedCards.add(cards[i]);
        return orderedCards.toArray(new Card[0]);
    }

    @Override
    public Card[] margrave_attack_cardsToKeep(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_margrave_attack_cardsToKeep(context)) {
            return super.margrave_attack_cardsToKeep(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(3).exactCount()
                .setPickType(PickType.KEEP).setCardResponsible(Cards.margrave);
        return getFromHand(context, sco);
    }

    @Override
    public Card getAttackReaction(MoveContext context, Card responsible, boolean defended, Card lastCard) {
        ArrayList<Card> reactionCards = new ArrayList<Card>();
        for (Card c : getReactionCards(defended)) {
            if (!c.equals(Cards.marketSquare) && !c.equals(Cards.watchTower)) 
            {
                reactionCards.add(c);
            }
        }
        if (reactionCards.size() > 0) {
            ArrayList<String> options = new ArrayList<String>();
            for (Card c : reactionCards)
                if (lastCard == null || !Game.suppressRedundantReactions || c.getName() != lastCard.getName() || c.equals(Cards.horseTraders) || c.equals(Cards.beggar))
                    options.add(Strings.getCardName(c));
            if (options.size() > 0) {
                String none = getString(R.string.none);
                options.add(none);
                String o = selectString(context, R.string.reaction_query, responsible, options.toArray(new String[0]));
                if(o.equals(none)) return null;
                return localNameToCard(o, reactionCards.toArray(new Card[0]));
            }
        }
        return null;
    }

    @Override
    public boolean revealBane(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_revealBane(context)) {
            return super.revealBane(context);
        }
        return selectBoolean(context, Cards.youngWitch, Strings.format(R.string.bane_option_one, Strings.getCardName(game.baneCard)), getString(R.string.pass));
    }

    @Override
    public PutBackOption selectPutBackOption(MoveContext context, List<PutBackOption> putBacks) {
        if(context.isQuickPlay() && shouldAutoPlay_selectPutBackOption(context, putBacks)) {
            return super.selectPutBackOption(context, putBacks);
        }
        // TODO(matt): this one looks like it'll be tricky.
        Collections.sort(putBacks);
        LinkedHashMap<String, PutBackOption> h = new LinkedHashMap<String, PutBackOption>();
        h.put(getCardName(Cards.treasury), PutBackOption.Treasury);
        h.put(getCardName(Cards.alchemist), PutBackOption.Alchemist);
        h.put(getCardName(Cards.walledVillage), PutBackOption.WalledVillage);
        h.put(getString(R.string.putback_option_one), PutBackOption.Coin);
        h.put(getString(R.string.putback_option_two), PutBackOption.Action);
        h.put(getString(R.string.none), PutBackOption.None);
        List<String> options = new ArrayList<String>();
        for (PutBackOption putBack : putBacks) {
            switch (putBack) {
                case Treasury:
                    options.add(getCardName(Cards.treasury));
                    break;
                case Alchemist:
                    options.add(getCardName(Cards.alchemist));
                    break;
                case WalledVillage:
                    options.add(getCardName(Cards.walledVillage));
                    break;
                case Coin:
                    options.add(getString(R.string.putback_option_one));
                    break;
                case Action:
                    options.add(getString(R.string.putback_option_two));
                    break;
                case None:
                    break;
                default:
                    break;
            }
        }
        options.add(getString(R.string.none));

        return h.get(selectString(context, getString(R.string.putback_query), options.toArray(new String[0])));
    }

    @Override
    public SquireOption squire_chooseOption(MoveContext context) {
        //      if(context.isQuickPlay() && shouldAutoPlay_steward_chooseOption(context)) {
        //          return super.steward_chooseOption(context);
        //      }
        SquireOption[] options = SquireOption.values();
        return options[selectOption(context, Cards.squire, options)];
    }

    @Override
    public Card armory_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_workshop_cardToObtain(context)) {
            return super.armory_cardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(4)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.armory);
        return getFromTable(context, sco);
    }

    @Override
    public Card altar_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_apprentice_cardToTrash(context)) {
            return super.altar_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.altar);
        return getCardFromHand(context, sco);
    }

    @Override
    public Card altar_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_workshop_cardToObtain(context)) {
            return super.altar_cardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(5)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.altar);
        return getFromTable(context, sco);
    }

    @Override
    public Card squire_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_feast_cardToObtain(context)) {
            return super.squire_cardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isAttack().setActionType(ActionType.GAIN)
                .setCardResponsible(Cards.squire);
        return getFromTable(context, sco);
    }

    @Override
    public Card rats_cardToTrash(MoveContext context) {
        if (context.isQuickPlay() && shouldAutoPlay_rats_cardToTrash(context)) {
            return super.rats_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isNonRats().setPickType(PickType.TRASH)
                .setActionType(ActionType.TRASH).setCardResponsible(Cards.rats);
        return getCardFromHand(context, sco);
    }

    @Override
    public boolean catacombs_shouldDiscardTopCards(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_navigator_shouldDiscardTopCards(context, cards)) {
            return super.catacombs_shouldDiscardTopCards(context, cards);
        }
        String header = "";
        for (Card c : cards)
            header += getCardName(c) + ", ";
        header += "--";
        header = header.replace(", --", "");
        header = Strings.format(R.string.catacombs_header, header);

        String option1 = getString(R.string.catacombs_option_one);
        String option2 = getString(R.string.catacombs_option_two);

        return !selectBoolean(context, header, option1, option2);
    }

    @Override
    public Card catacombs_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_feast_cardToObtain(context)) {
            return super.catacombs_cardToObtain(context);
        }
        int maxPrice = Math.max(0, game.getPile(Cards.catacombs).card().getCost(context) - 1);
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(maxPrice)
                .setActionType(ActionType.GAIN).setCardResponsible(Cards.catacombs);
        return getFromTable(context, sco);
    }

    @Override
    public CountFirstOption count_chooseFirstOption(MoveContext context) {
        CountFirstOption[] options = CountFirstOption.values();
        return options[selectOption(context, Cards.count, options)];
    }

    @Override
    public CountSecondOption count_chooseSecondOption(MoveContext context) {
        CountSecondOption[] options = CountSecondOption.values();
        return options[selectOption(context, Cards.count, options)];
    }

    @Override
    public Card[] count_cardsToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_torturer_attack_cardsToDiscard(context)) {
            return super.count_cardsToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount().setPickType(PickType.DISCARD);
        return getFromHand(context, getActionString(ActionType.DISCARD, Cards.count), sco);
    }

    @Override
    public Card count_cardToPutBackOnDeck(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_courtyard_cardToPutBackOnDeck(context)) {
            return super.count_cardToPutBackOnDeck(context);
        }
        SelectCardOptions sco = new SelectCardOptions();
        return getCardFromHand(context, Strings.format(R.string.count_part_top_of_deck, getCardName(Cards.count)), sco);
    }
    @Override
    public Card deathCart_actionToTrash(MoveContext context) {
        SelectCardOptions sco = new SelectCardOptions().isAction().setPassable(getString(R.string.none)).setPickType(PickType.TRASH);
        return getCardFromHand(context, getActionString(ActionType.TRASH, Cards.deathCart), sco);
    }

    @Override
    public Card forager_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_apprentice_cardToTrash(context)) {
            return super.forager_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH);
        return getCardFromHand(context, getActionString(ActionType.TRASH, Cards.forager), sco);

    }

    @Override
    public GraverobberOption graverobber_chooseOption(MoveContext context) {
        GraverobberOption[] options = GraverobberOption.values();
        return options[selectOption(context, Cards.graverobber, options)];
    }

    @Override
    public Card graverobber_cardToGainFromTrash(MoveContext context) {
        LinkedHashMap<String, Card> h = new LinkedHashMap<String, Card>();
        ArrayList<Card> options = new ArrayList<Card>();

        for (Card c : game.trashPile) {
            if (c.getCost(context) >= 3 && c.getCost(context) <= 6)
                options.add(c);
        }

        if (options.isEmpty()) {
            return null;
        }

        for (Card c : options) {
            h.put(c.getName(), c);
        }

        return h.get(selectString(context, Cards.graverobber, h.keySet().toArray(new String[0])));
    }

    @Override
    public Card graverobber_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_apprentice_cardToTrash(context)) {
            return super.graverobber_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isAction().setPickType(PickType.TRASH);
        return getCardFromHand(context, getActionString(ActionType.TRASH, Cards.graverobber), sco);
    }

    @Override
    public Card graverobber_cardToReplace(MoveContext context, int maxCost, boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_expand_cardToObtain(context, maxCost, potion)) {
            return super.graverobber_cardToReplace(context, maxCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().maxCost(maxCost).potionCost(potion ? 1 : 0);
        return getFromTable(context, getActionString(ActionType.GAIN, Cards.graverobber), sco);
    }

    @Override
    public HuntingGroundsOption huntingGrounds_chooseOption(MoveContext context) {
        HuntingGroundsOption[] options = HuntingGroundsOption.values();
        return options[selectOption(context, Cards.huntingGrounds, options)];
    }

    @Override
    public boolean ironmonger_shouldDiscard(MoveContext context, Card card) {
        return !selectBooleanCardRevealed(context, Cards.ironmonger, card, getString(R.string.ironmonger_option_one), getString(R.string.discard));
    }

    @Override
    public Card junkDealer_cardToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_trader_cardToTrash(context)) {
            return super.junkDealer_cardToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH);
        return getCardFromHand(context, getActionString(ActionType.TRASH, Cards.junkDealer), sco);
    }

    @Override
    public boolean marketSquare_shouldDiscard(MoveContext context) {
        return selectBoolean(context, Cards.marketSquare, getString(R.string.discard), getString(R.string.keep));
    }

    @Override
    public Card mystic_cardGuess(MoveContext context, ArrayList<Card> cardList) {

        if(context.isQuickPlay() && shouldAutoPlay_wishingWell_cardGuess(context)) {
            return super.mystic_cardGuess(context, cardList);
        }

        LinkedHashMap<String, Card> h = new LinkedHashMap<String, Card>();

        // Add option to skip the guess
        h.put("None", null);

        for (Card c : cardList) {
            h.put(c.getName(), c);
        }

        String choice = selectString(context, getActionString(ActionType.NAMECARD, Cards.mystic), h.keySet().toArray(new String[0])); 

        return h.get(choice);
    }

    @Override
    public boolean scavenger_shouldDiscardDeck(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_chancellor_shouldDiscardDeck(context)) {
            return super.scavenger_shouldDiscardDeck(context);
        }
        return selectBoolean(context, getCardName(Cards.scavenger), getString(R.string.chancellor_query), getString(R.string.pass));
    }

    @Override
    public Card scavenger_cardToPutBackOnDeck(MoveContext context) {
        CardList localDiscard = (context.player.isPossessed()) ? context.player.getDiscard() : getDiscard();
        if (localDiscard.isEmpty())
            return null;

        // oh, how simple it looks, and how ugly it will be (again)
        LinkedHashMap<String, Card> h = new LinkedHashMap<String, Card>();
        for (Card c : localDiscard) {
            h.put(c.getName(), c);
        }
        return h.get(selectString(context, Cards.scavenger, h.keySet().toArray(new String[0])));
    }

    @Override
    public Card[] storeroom_cardsToDiscardForCards(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_cellar_cardsToDiscard(context)) {
            return super.storeroom_cardsToDiscardForCards(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none)).setPickType(PickType.DISCARD);
        return getFromHand(context, getActionString(ActionType.DISCARDFORCARD, Cards.storeroom), sco);
    }

    @Override
    public Card[] storeroom_cardsToDiscardForCoins(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_cellar_cardsToDiscard(context)) {
            return super.storeroom_cardsToDiscardForCoins(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setPassable(getString(R.string.none)).setPickType(PickType.DISCARD);
        return getFromHand(context, getActionString(ActionType.DISCARDFORCOIN, Cards.storeroom), sco);
    }

    @Override
    public ActionCard procession_cardToPlay(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_throneRoom_cardToPlay(context)) {
            return super.procession_cardToPlay(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isAction().setPassable(getString(R.string.none)).setPickType(PickType.PLAY);
        return (ActionCard) getCardFromHand(context, getCardName(Cards.procession), sco);
    }

    @Override
    public Card procession_cardToGain(MoveContext context, int exactCost,   boolean potion) {
        if(context.isQuickPlay() && shouldAutoPlay_procession_cardToObtain(context, exactCost, potion)) {
            return super.procession_cardToGain(context, exactCost, potion);
        }
        SelectCardOptions sco = new SelectCardOptions().isAction().exactCost(exactCost).potionCost(potion ? 1 : 0);
        return getFromTable(context, getActionString(ActionType.GAIN, Cards.procession), sco);
    }

    @Override
    public Card rebuild_cardToPick(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_feast_cardToObtain(context)) {
            return super.rebuild_cardToPick(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isVictory().allowEmpty();
        return getFromTable(context, getActionString(ActionType.NAMECARD, Cards.rebuild), sco);
    }

    @Override
    public Card rebuild_cardToGain(MoveContext context, int maxCost, boolean costPotion) {
        if(context.isQuickPlay() && shouldAutoPlay_remodel_cardToObtain(context, maxCost, costPotion)) {
            return super.rebuild_cardToGain(context, maxCost, costPotion);
        }
        SelectCardOptions sco = new SelectCardOptions().isVictory().maxCost(maxCost).potionCost(costPotion ? 1 : 0);
        return getFromTable(context, getActionString(ActionType.GAIN, Cards.rebuild), sco);
    }

    @Override
    public Card rogue_cardToGain(MoveContext context) {
        LinkedHashMap<String, Card> h = new LinkedHashMap<String, Card>();
        ArrayList<Card> options = new ArrayList<Card>(); 

        for (Card c : game.trashPile) {
            if (c.getCost(context) >= 3 && c.getCost(context) <= 6)
                options.add(c);
        }

        if (options.isEmpty()) {
            return null;
        }

        for (Card c : options) {
            h.put(c.getName(), c);
        }

        return h.get(selectString(context, Cards.rogue, h.keySet().toArray(new String[0])));
    }

    @Override
    public Card rogue_cardToTrash(MoveContext context, ArrayList<Card> canTrash) {
        LinkedHashMap<String, Card> h = new LinkedHashMap<String, Card>();
        for (Card c : canTrash) {
            h.put(c.getName(), c);
        }

        return h.get(selectString(context, Cards.rogue, h.keySet().toArray(new String[0])));
    }

    @Override
    public TreasureCard counterfeit_cardToPlay(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_masquerade_cardToTrash(context)) {
            return super.counterfeit_cardToPlay(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isTreasure().setPassable(getString(R.string.none)).setPickType(PickType.TRASH);
        return (TreasureCard) getCardFromHand(context, getActionString(ActionType.TRASH, Cards.counterfeit), sco);
    }

    @Override
    public Card pillage_opponentCardToDiscard(MoveContext context, ArrayList<Card> handCards)
    {
        if(context.isQuickPlay() && shouldAutoPlay_pillage_opponentCardToDiscard(context)) 
        {
            return super.pillage_opponentCardToDiscard(context, handCards);
        }

        ArrayList<String> options = new ArrayList<String>();

        for (Card c : handCards)
        {
            options.add(Strings.getCardName(c));
        }

        if (!options.isEmpty()) 
        {
            String o = selectString(context, getActionString(ActionType.OPPONENTDISCARD, Cards.pillage, context.attackedPlayer.getPlayerName()), options.toArray(new String[0]));
            return (Card) localNameToCard(o, handCards.toArray(new Card[0]));
        } 
        else 
        {
            return null;
        }
    }

    @Override
    public boolean hovel_shouldTrash(MoveContext context)
    {
        if(context.isQuickPlay())
        {
            return true;
        }
        else
        {
            return selectBoolean(context, getCardName(Cards.hovel), getString(R.string.hovel_option), getString(R.string.pass));
        }
    }

    @Override
    public boolean walledVillage_backOnDeck(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_walledVillage_backOnDeck(context)) {
            return super.walledVillage_backOnDeck(context);
        }
        String option1 = getString(R.string.walledVillage_option_one);
        String option2 = getString(R.string.walledVillage_option_two);
        return selectBoolean(context, Cards.walledVillage, option1, option2);
    }

    @Override
    public GovernorOption governor_chooseOption(MoveContext context) {
        GovernorOption[] options = GovernorOption.values();
        return options[selectOption(context, Cards.governor, options)];
    }

    @Override
    public Card envoy_cardToDiscard(MoveContext context, Card[] cards) {
        if(context.isQuickPlay() && shouldAutoPlay_envoy_opponentCardToDiscard(context)) {
            return super.envoy_cardToDiscard(context, cards);
        }

        ArrayList<String> options = new ArrayList<String>();

        for (Card c : cards) {
            options.add(Strings.getCardName(c));
        }

        if (!options.isEmpty()) {
            String o = selectString(context, getActionString(ActionType.OPPONENTDISCARD, Cards.envoy, context.getPlayer().getPlayerName()), options.toArray(new String[0]));
            return (Card) localNameToCard(o, cards);
        } else {
            return null;
        }
    }

    @Override
    public boolean survivors_shouldDiscardTopCards(MoveContext context, Card[] array) {
        if(context.isQuickPlay() && shouldAutoPlay_navigator_shouldDiscardTopCards(context, array)) {
            return super.survivors_shouldDiscardTopCards(context, array);
        }
        String header = "";
        for (Card c : array)
            header += getCardName(c) + ", ";
        header += "--";
        header = header.replace(", --", "");
        header = Strings.format(R.string.survivors_header, header);

        String option1 = getString(R.string.discard);
        String option2 = getString(R.string.navigator_option_two);

        return selectBoolean(context, header, option1, option2);
    }
    @Override
    public Card[] survivors_cardOrder(MoveContext context, Card[] array) {
        if(context.isQuickPlay() && shouldAutoPlay_navigator_cardOrder(context, array)) {
            return super.survivors_cardOrder(context, array);
        }
        ArrayList<Card> orderedCards = new ArrayList<Card>();
        int[] order = orderCards(context, cardArrToIntArr(array));
        for (int i : order) {
            orderedCards.add(array[i]);
        }
        return orderedCards.toArray(new Card[0]);
    }
    @Override
    public boolean cultist_shouldPlayNext(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_cultist_shouldPlayNext(context)) {
            return super.cultist_shouldPlayNext(context);
        }

        String option1 = getString(R.string.cultist_play_next);
        String option2 = getString(R.string.pass);

        return selectBoolean(context, Cards.cultist.getName(), option1, option2);
    }

    @Override
    public Card[] urchin_attack_cardsToKeep(MoveContext context) {
        //if(context.isQuickPlay() && shouldAutoPlay_urchin_attack_cardsToKeep(context)) {
        //    return super.urchin_attack_cardsToKeep(context);
        //}

        SelectCardOptions sco = new SelectCardOptions().setCount(4).exactCount().setPickType(PickType.KEEP);
        return getFromHand(context, getString(R.string.urchin_keep), sco);
    }

    @Override
    public boolean urchin_shouldTrashForMercenary(MoveContext context)
    {
        if(context.isQuickPlay() && shouldAutoPlay_urchin_shouldTrashForMercenary(context)) {
            return super.urchin_shouldTrashForMercenary(context);
        }

        String option1 = getString(R.string.urchin_trash_for_mercenary);
        String option2 = getString(R.string.pass);

        return selectBoolean(context, Cards.urchin.getName(), option1, option2);
    }

    @Override
    public Card[] mercenary_cardsToTrash(MoveContext context) {
        //if(context.isQuickPlay() && shouldAutoPlay_mercenary_cardsToTrash(context)) {
        //    return super.mercenary_cardsToTrash(context);
        //}
        SelectCardOptions sco = new SelectCardOptions().setCount(2).exactCount().setPassable(getString(R.string.none)).setPickType(PickType.TRASH);
        return getFromHand(context, getActionString(ActionType.TRASH, Cards.mercenary), sco);
    }

    @Override
    public Card[] mercenary_attack_cardsToKeep(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_militia_attack_cardsToKeep(context)) {
            return super.mercenary_attack_cardsToKeep(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(3).exactCount().setPickType(PickType.KEEP);
        return getFromHand(context, getString(R.string.mercenary_part), sco);
    }

    @Override
    public boolean madman_shouldReturnToPile(MoveContext context)
    {
        if(context.isQuickPlay() && shouldAutoPlay_madman_shouldReturnToPile(context)) {
            return super.madman_shouldReturnToPile(context);
        }

        String option1 = getString(R.string.madman_option);
        String option2 = getString(R.string.pass);

        return selectBoolean(context, Cards.madman.getName(), option1, option2);
    }

    @Override
    public Card hermit_cardToTrash(MoveContext context, ArrayList<Card> cardList, int nonTreasureCountInDiscard)
    {
        LinkedHashMap<String, Card> h = new LinkedHashMap<String, Card>();

        int cardCount = 0;

        // Add option to skip the trashing
        h.put("None", null);

        for (Card c : cardList) {
            if (cardCount < nonTreasureCountInDiscard) {
                h.put(c.getName() + " (discard pile)", c);
            } else {
                h.put(c.getName() + " (hand)", c);
            }

            ++cardCount;
        }

        String choice = selectString(context, getActionString(ActionType.TRASH, Cards.hermit), h.keySet().toArray(new String[0])); 

        if (choice.contains("discard pile")) {
            context.hermitTrashCardPile = PileSelection.DISCARD;
        } else if (choice.contains("hand")) {
            context.hermitTrashCardPile = PileSelection.HAND;
        } 

        return h.get(choice);
    }

    @Override
    public Card hermit_cardToGain(MoveContext context)  {
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(3);
        return getFromTable(context, getActionString(ActionType.GAIN, Cards.hermit), sco);
    }

    @Override
    public Card[] dameAnna_cardsToTrash(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_chapel_cardsToTrash(context)) {
            return super.dameAnna_cardsToTrash(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(2).setPassable(getString(R.string.none)).setPickType(PickType.TRASH);
        return getFromHand(context, getActionString(ActionType.TRASH, Cards.dameAnna), sco);
    }
    @Override
    public Card knight_cardToTrash(MoveContext context, ArrayList<Card> canTrash) {
        LinkedHashMap<String, Card> h = new LinkedHashMap<String, Card>();
        for (Card c : canTrash) {
            h.put(c.getName(), c);
        }
        return h.get(selectString(context, Cards.virtualKnight, h.keySet().toArray(new String[0])));
    }
    @Override
    public Card[] sirMichael_attack_cardsToKeep(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_militia_attack_cardsToKeep(context)) {
            return super.sirMichael_attack_cardsToKeep(context);
        }
        SelectCardOptions sco = new SelectCardOptions().setCount(3).exactCount().setPickType(PickType.KEEP);
        return getFromHand(context, getString(R.string.sir_michael_part), sco);
    }
    @Override
    public Card dameNatalie_cardToObtain(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_workshop_cardToObtain(context)) {
            return super.dameNatalie_cardToObtain(context);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(3).setPassable(getString(R.string.none));
        return getFromTable(context, getActionString(ActionType.GAIN, Cards.dameNatalie), sco);
    }

    @Override
    public ActionCard bandOfMisfits_actionCardToImpersonate(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_bandOfMisfits_actionCardToImpersonate(context)) {
            return super.bandOfMisfits_actionCardToImpersonate(context);
        }
        SelectCardOptions sco = new SelectCardOptions().potionCost(0).maxCost(Cards.bandOfMisfits.getCost(context) - 1).isAction().setPassable(getString(R.string.none));
        return (ActionCardImpl) getFromTable(context, getString(R.string.part_play), sco);
    }

    @Override
    public TreasureCard taxman_treasureToTrash(MoveContext context) 
    {    
        SelectCardOptions sco = new SelectCardOptions().isTreasure().setPassable(getString(R.string.none)).setPickType(PickType.TRASH);
        return (TreasureCard) getCardFromHand(context, getActionString(ActionType.TRASH, Cards.taxman), sco);
    }

    @Override
    public TreasureCard taxman_treasureToObtain(MoveContext context, int maxCost) {
        if(context.isQuickPlay() && shouldAutoPlay_taxman_treasureToObtain(context, maxCost)) {
            return super.taxman_treasureToObtain(context, maxCost);
        }
        SelectCardOptions sco = new SelectCardOptions().isTreasure().maxCost(maxCost);
        return (TreasureCard) getFromTable(context, getString(R.string.taxman_part), sco);
    }

    @Override
    public TreasureCard plaza_treasureToDiscard(MoveContext context) {
        if(context.isQuickPlay() && shouldAutoPlay_stables_treasureToDiscard(context)) {
            return super.stables_treasureToDiscard(context);
        }
        SelectCardOptions sco = new SelectCardOptions().isTreasure().setPassable(getString(R.string.none)).setPickType(PickType.DISCARD);
        return (TreasureCard) getCardFromHand(context, getActionString(ActionType.DISCARD, Cards.plaza), sco);
    }

    @Override
    public int numGuildsCoinTokensToSpend(MoveContext context)
    {
        return selectInt(context, "Spend Guilds Coin Tokens", getGuildsCoinTokenCount(), 0);
    }

    @Override
    public int amountToOverpay(MoveContext context, int cardCost)
    {
        int availableAmount = context.getCoinAvailableForBuy() - cardCost;

        // If at least one potion is available, it can be used to overpay
        int potion = context.potions;

        if (availableAmount <= 0)
        {
            return 0;
        }
        else
        {
            return selectInt(context, "Overpay?", availableAmount, 0);
        }
    }

    @Override
    public int overpayByPotions(MoveContext context, int availablePotions)
    {
        if (availablePotions > 0)
        {
            return selectInt(context, "Overpay by Potion(s)?", availablePotions, 0);
        }
        else
        {
            return 0;
        }
    }

    @Override
    public Card butcher_cardToTrash(MoveContext context) 
    {
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH).setPassable(getString(R.string.none));
        return getCardFromHand(context, getActionString(ActionType.TRASH, Cards.butcher), sco);
    }

    @Override
    public Card butcher_cardToObtain(MoveContext context, int maxCost, boolean potion)
    {        
        SelectCardOptions sco = new SelectCardOptions().maxCost(maxCost).potionCost(potion ? 1 : 0);
        return getFromTable(context, getActionString(ActionType.GAIN, Cards.butcher), sco);
    }

    @Override
    public Card advisor_cardToDiscard(MoveContext context, Card[] cards)
    {
        ArrayList<String> options = new ArrayList<String>();

        for (Card c : cards) 
        {
            options.add(Strings.getCardName(c));
        }

        if (!options.isEmpty()) 
        {
            String o = selectString(context, getActionString(ActionType.OPPONENTDISCARD, Cards.advisor, context.getPlayer().getPlayerName()), options.toArray(new String[0]));
            return (Card) localNameToCard(o, cards);
        } 
        else 
        {
            return null;
        }
    }

    @Override
    public Card journeyman_cardToPick(MoveContext context) 
    {
        SelectCardOptions sco = new SelectCardOptions().allowEmpty();
        return getFromTable(context, getActionString(ActionType.NAMECARD, Cards.journeyman), sco);
    }

    @Override
    public Card stonemason_cardToTrash(MoveContext context)
    {
        SelectCardOptions sco = new SelectCardOptions().setPickType(PickType.TRASH).allowEmpty();
        return getCardFromHand(context, getActionString(ActionType.TRASH, Cards.stonemason), sco);
    }

    @Override
    public Card stonemason_cardToGain(MoveContext context, int maxCost, boolean potion)
    {
        SelectCardOptions sco = new SelectCardOptions().allowEmpty().maxCost(maxCost).potionCost(potion ? 1 : 0);
        return getFromTable(context, getActionString(ActionType.GAIN, Cards.stonemason), sco);
    }

    @Override
    public Card stonemason_cardToGainOverpay(MoveContext context, int overpayAmount, boolean potion)
    {
        SelectCardOptions sco = new SelectCardOptions().allowEmpty().exactCost(overpayAmount).isAction().potionCost(potion ? 1 : 0);
        return getFromTable(context, getActionString(ActionType.GAIN, Cards.stonemason), sco);
    }

    @Override
    public Card doctor_cardToPick(MoveContext context)
    {
        SelectCardOptions sco = new SelectCardOptions().allowEmpty();
        return getFromTable(context, getActionString(ActionType.NAMECARD, Cards.doctor), sco);
    }

    @Override
    public ArrayList<Card> doctor_cardsForDeck(MoveContext context, ArrayList<Card> cards) 
    {
        ArrayList<Card> orderedCards = new ArrayList<Card>();

        int[] order = orderCards(context, cardArrToIntArr(cards.toArray(new Card[0])));

        for (int i : order)
        {
            orderedCards.add(cards.get(i));
        }

        return orderedCards;
    }

    @Override
    public DoctorOverpayOption doctor_chooseOption(MoveContext context, Card card) {
        /*
         * TODO(matt): this needs to be done like Jester, whenever I figure that card out.
        DoctorOverpayOption[] options = DoctorOverpayOption.values();
        return options[selectOption(context, Cards.doctor, options)];
        */
        LinkedHashMap<String, DoctorOverpayOption> optionMap = new LinkedHashMap<String, DoctorOverpayOption>();

        optionMap.put(getString(R.string.doctor_overpay_option_one),   DoctorOverpayOption.TrashIt);
        optionMap.put(getString(R.string.doctor_overpay_option_two),   DoctorOverpayOption.DiscardIt);
        optionMap.put(getString(R.string.doctor_overpay_option_three), DoctorOverpayOption.PutItBack);

        return optionMap.get(selectString(context, "Doctor revealed " + getCardName(card), optionMap.keySet().toArray(new String[0])));
    }

    @Override
    public Card herald_cardTopDeck(MoveContext context, Card[] cardList)
    {
        ArrayList<String> options = new ArrayList<String>();

        // Remove first Herald from this list (representing the most recent one bought)
        boolean heraldRemoved = false;

        for (Card c : cardList) 
        {
            if (!heraldRemoved && c.getName().equalsIgnoreCase("herald"))
            {
                heraldRemoved = true;
            }
            else
            {
                options.add(Strings.getCardName(c));
            }
        }

        if (!options.isEmpty()) 
        {
            String o = selectString(context, R.string.herald_overpay_query, Cards.herald, options.toArray(new String[0]));
            return (Card) localNameToCard(o, cardList);
        } 
        else 
        {
            return null;
        }
    }
}
