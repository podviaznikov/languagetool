/* LanguageTool, a natural language style checker 
 * Copyright (C) 2011 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.dev.wikipedia;

import junit.framework.TestCase;
import org.languagetool.language.German;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class WikipediaQuickCheckTest extends TestCase {

  // only for interactive use, as it accesses a remote API
  public void noTestCheckPage() throws IOException, PageNotFoundException {
    final WikipediaQuickCheck check = new WikipediaQuickCheck();
    //final String url = "http://de.wikipedia.org/wiki/Benutzer_Diskussion:Dnaber";
    //final String url = "http://de.wikipedia.org/wiki/OpenThesaurus";
    //final String url = "http://de.wikipedia.org/wiki/Gütersloh";
    //final String url = "http://de.wikipedia.org/wiki/Bielefeld";
    final String url = "http://de.wikipedia.org/wiki/Augsburg";
    final MarkupAwareWikipediaResult result = check.checkPage(new URL(url));
    final List<AppliedRuleMatch> appliedMatches = result.getAppliedRuleMatches();
    System.out.println("ruleApplications: " + appliedMatches.size());
    for (AppliedRuleMatch appliedMatch : appliedMatches) {
      System.out.println("=====");
      System.out.println("Rule     : " + appliedMatch.getRuleMatch().getRule().getDescription() + "\n");
      for (RuleMatchApplication ruleMatchApplication : appliedMatch.getRuleMatchApplications()) {
        System.out.println("Original : " + ruleMatchApplication.getOriginalErrorContext(10).replace("\n", " "));
        if (ruleMatchApplication.hasRealReplacement()) {
          System.out.println("Corrected: " + ruleMatchApplication.getCorrectedErrorContext(10).replace("\n", " "));
        }
        System.out.println();
      }
    }
  }

  public void testCheckWikipediaMarkup() throws IOException {
    final WikipediaQuickCheck check = new WikipediaQuickCheck();
    final String markup = "== Beispiele ==\n\n" +
            "Eine kleine Auswahl von Fehlern.\n\n" +
            "Das Komma ist richtig, wegen dem Leerzeichen.";
    final MediaWikiContent wikiContent = new MediaWikiContent(markup, "2012-11-11T20:00:00");
    final MarkupAwareWikipediaResult result = check.checkWikipediaMarkup(new URL("http://fake-url.org"), wikiContent, new German());
    assertThat(result.getLastEditTimestamp(), is("2012-11-11T20:00:00"));
    final List<AppliedRuleMatch> appliedMatches = result.getAppliedRuleMatches();
    // even though this error has no suggestion, there's a (pseudo) correction:
    assertThat(appliedMatches.size(), is(1));
    final AppliedRuleMatch firstAppliedMatch = appliedMatches.get(0);
    assertThat(firstAppliedMatch.getRuleMatchApplications().size(), is(1));
    RuleMatchApplication ruleMatchApplication = firstAppliedMatch.getRuleMatchApplications().get(0);
    assertTrue("Got: " + ruleMatchApplication.getTextWithCorrection(),
            ruleMatchApplication.getTextWithCorrection().contains("<span class=\"error\">wegen dem</span> Leerzeichen."));
    assertThat(ruleMatchApplication.getOriginalErrorContext(10), is(" richtig, <span class=\"error\">wegen dem</span> Le"));
    assertThat(ruleMatchApplication.getCorrectedErrorContext(10), is(" richtig, <span class=\"error\">wegen dem</span> Le"));
  }

  public void testGetPlainText() {
    final WikipediaQuickCheck check = new WikipediaQuickCheck();
    final String filteredContent = check.getPlainText(
            "<?xml version=\"1.0\"?><api><query><normalized><n from=\"Benutzer_Diskussion:Dnaber\" to=\"Benutzer Diskussion:Dnaber\" />" +
                    "</normalized><pages><page pageid=\"143424\" ns=\"3\" title=\"Benutzer Diskussion:Dnaber\"><revisions><rev xml:space=\"preserve\">\n" +
                    "Test [[Link]] Foo&amp;nbsp;bar.\n" +
                    "</rev></revisions></page></pages></query></api>");
    assertEquals("Test Link Foo bar.", filteredContent);
  }

  public void testGetPlainTextMapping() {
    final WikipediaQuickCheck check = new WikipediaQuickCheck();
    final String text = "Test [[Link]] und [[AnotherLink|noch einer]] und [http://test.org external link] Foo&amp;nbsp;bar.\n";
    final PlainTextMapping filteredContent = check.getPlainTextMapping(
            "<?xml version=\"1.0\"?><api><query><normalized><n from=\"Benutzer_Diskussion:Dnaber\" to=\"Benutzer Diskussion:Dnaber\" />" +
                    "</normalized><pages><page pageid=\"143424\" ns=\"3\" title=\"Benutzer Diskussion:Dnaber\"><revisions><rev xml:space=\"preserve\">" +
                    text +
                    "</rev></revisions></page></pages></query></api>");

    assertEquals("Test Link und noch einer und external link Foo bar.", filteredContent.getPlainText());
    assertEquals(1, filteredContent.getOriginalTextPositionFor(1).line);
    assertEquals(1, filteredContent.getOriginalTextPositionFor(1).column);
    assertEquals(filteredContent.getPlainText().charAt(0), text.charAt(0));

    assertEquals('u', text.charAt(14));  // note that these are zero-based, the others are not
    assertEquals('u', filteredContent.getPlainText().charAt(10));
    assertEquals(1, filteredContent.getOriginalTextPositionFor(11).line);
    assertEquals(15, filteredContent.getOriginalTextPositionFor(11).column);
  }

  public void testGetPlainTextMappingMultiLine1() {
    final WikipediaQuickCheck check = new WikipediaQuickCheck();
    final String text = "Test [[Link]] und [[AnotherLink|noch einer]].\nUnd [[NextLink]] Foobar.\n";
    final PlainTextMapping filteredContent = check.getPlainTextMapping(
            "<?xml version=\"1.0\"?><api><query><normalized><n from=\"Benutzer_Diskussion:Dnaber\" to=\"Benutzer Diskussion:Dnaber\" />" +
                    "</normalized><pages><page pageid=\"143424\" ns=\"3\" title=\"Benutzer Diskussion:Dnaber\"><revisions><rev xml:space=\"preserve\">" +
                    text +
                    "</rev></revisions></page></pages></query></api>");
    assertEquals("Test Link und noch einer. Und NextLink Foobar.", filteredContent.getPlainText());
    assertEquals(1, filteredContent.getOriginalTextPositionFor(1).line);
    assertEquals(1, filteredContent.getOriginalTextPositionFor(1).column);
    assertEquals(filteredContent.getPlainText().charAt(0), text.charAt(0));

    assertEquals('U', text.charAt(46));  // note that these are zero-based, the others are not
    assertEquals(' ', filteredContent.getPlainText().charAt(25));
    assertEquals('U', filteredContent.getPlainText().charAt(26));
    assertEquals(2, filteredContent.getOriginalTextPositionFor(27).line);

    assertEquals(45, filteredContent.getOriginalTextPositionFor(25).column);
    assertEquals(1, filteredContent.getOriginalTextPositionFor(26).column);
    assertEquals(2, filteredContent.getOriginalTextPositionFor(27).column);
  }

  public void testGetPlainTextMappingMultiLine2() {
    final WikipediaQuickCheck check = new WikipediaQuickCheck();
    final String text = "Test [[Link]] und [[AnotherLink|noch einer]].\n\nUnd [[NextLink]] Foobar.\n";
    final PlainTextMapping filteredContent = check.getPlainTextMapping(
            "<?xml version=\"1.0\"?><api><query><normalized><n from=\"Benutzer_Diskussion:Dnaber\" to=\"Benutzer Diskussion:Dnaber\" />" +
                    "</normalized><pages><page pageid=\"143424\" ns=\"3\" title=\"Benutzer Diskussion:Dnaber\"><revisions><rev xml:space=\"preserve\">" +
                    text +
                    "</rev></revisions></page></pages></query></api>");
    assertEquals("Test Link und noch einer.\n\nUnd NextLink Foobar.", filteredContent.getPlainText());
    assertEquals(1, filteredContent.getOriginalTextPositionFor(1).line);
    assertEquals(1, filteredContent.getOriginalTextPositionFor(1).column);
    assertEquals(filteredContent.getPlainText().charAt(0), text.charAt(0));

    assertEquals('U', text.charAt(47));  // note that these are zero-based, the others are not
    assertEquals('U', filteredContent.getPlainText().charAt(27));
    assertEquals(3, filteredContent.getOriginalTextPositionFor(28).line);
    assertEquals(45, filteredContent.getOriginalTextPositionFor(25).column);
    assertEquals(46, filteredContent.getOriginalTextPositionFor(26).column);
    assertEquals(47, filteredContent.getOriginalTextPositionFor(27).column);
    assertEquals(1, filteredContent.getOriginalTextPositionFor(28).column);
  }

  public void testRemoveInterLanguageLinks() {
    final WikipediaQuickCheck check = new WikipediaQuickCheck();
    assertEquals("foo  bar", check.removeWikipediaLinks("foo [[pt:Some Article]] bar"));
    assertEquals("foo [[some link]] bar", check.removeWikipediaLinks("foo [[some link]] bar"));
    assertEquals("foo [[Some Link]] bar ", check.removeWikipediaLinks("foo [[Some Link]] bar [[pt:Some Article]]"));
    assertEquals("foo [[zh-min-nan:Linux]] bar", check.removeWikipediaLinks("foo [[zh-min-nan:Linux]] bar"));  // known limitation
    assertEquals("[[Scultura bronzea di Gaudí mentre osserva il suo ''[[Il Capriccio|Capriccio]]'']]", check.removeWikipediaLinks("[[File:Gaudì-capriccio.JPG|thumb|left|Scultura bronzea di Gaudí mentre osserva il suo ''[[Il Capriccio|Capriccio]]'']]"));
    assertEquals("[[[[Palau de la Música Catalana]], entrada]]", check.removeWikipediaLinks("[[Fitxer:Palau_de_musica_2.JPG|thumb|[[Palau de la Música Catalana]], entrada]]"));
    assertEquals("foo  bar", check.removeWikipediaLinks("foo [[Kategorie:Kurgebäude]] bar"));
    assertEquals("foo [[''Kursaal Palace'' in San Sebastián]] bar", check.removeWikipediaLinks("foo [[Datei:FestivalSS.jpg|miniatur|''Kursaal Palace'' in San Sebastián]] bar"));
  }

}
