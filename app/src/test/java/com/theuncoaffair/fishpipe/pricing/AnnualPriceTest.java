package com.theuncoaffair.fishpipe.pricing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AnnualPriceTest {
    @Test
    public void nigeriaUsesNairaCheckoutCurrency() {
        AnnualPrice price = AnnualPrice.forRegion("ng");
        assertEquals("₦1,100.00", price.display());
        assertEquals("NGN", price.currencyCode());
        assertEquals("https://selar.com/sp4j7498fu?currency=NGN", price.checkoutUrl());
    }

    @Test
    public void regionIsCaseInsensitive() {
        AnnualPrice price = AnnualPrice.forRegion("GB");
        assertEquals("£3.00", price.display());
        assertEquals("GBP", price.currencyCode());
    }

    @Test
    public void unitedStatesUsesUsdCheckoutCurrency() {
        AnnualPrice price = AnnualPrice.forRegion("US");
        assertEquals("$3.00", price.display());
        assertEquals("USD", price.currencyCode());
        assertEquals("https://selar.com/sp4j7498fu?currency=USD", price.checkoutUrl());
    }

    @Test
    public void supportedAfricanCurrenciesMatchSelar() {
        assertEquals("GH¢10.32", AnnualPrice.forRegion("GH").display());
        assertEquals("KSh116.32", AnnualPrice.forRegion("KE").display());
        assertEquals("R14.41", AnnualPrice.forRegion("ZA").display());
        assertEquals("TSh2,500.00", AnnualPrice.forRegion("TZ").display());
        assertEquals("USh3,535", AnnualPrice.forRegion("UG").display());
        assertEquals("CFA562", AnnualPrice.forRegion("CM").display());
        assertEquals("CFA551", AnnualPrice.forRegion("SN").display());
        assertEquals("R₣1,287", AnnualPrice.forRegion("RW").display());
        assertEquals("ZMW50.00", AnnualPrice.forRegion("ZM").display());
        assertEquals("Le100.00", AnnualPrice.forRegion("SL").display());
    }

    @Test
    public void unsupportedCurrencyFallsBackToUsd() {
        AnnualPrice price = AnnualPrice.forRegion("DE");
        assertEquals("$3.00", price.display());
        assertEquals("USD", price.currencyCode());
    }

    @Test
    public void unknownRegionFallsBackToUsd() {
        assertEquals("$3.00", AnnualPrice.forRegion("ZZ").display());
        assertEquals("$3.00", AnnualPrice.forRegion(null).display());
        assertEquals("USD", AnnualPrice.forRegion(null).currencyCode());
    }
}
