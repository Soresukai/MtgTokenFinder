package tokenfinder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import tokenfinder.ScryfallDataManager.ImageSize;

@Controller
public class CardController {

    @GetMapping({"/", "/tokens", "/fromurl"})
    public String cards(Model model) {
        return "redirect:/search";
    }
    
    @GetMapping("/search")
    public String search(Model model) {
    	model.addAttribute("sites", URL_Processor.SupportedSites);
        return "search";
    }
    
    @GetMapping("/about")
    public String about(Model model) {
        return "about";
    }
    
    @GetMapping("/contact")
    public String contact(Model model) {
    	return "redirect:" + ScryfallDataManager.googleformURL();
    }
    
    @PostMapping("/tokens")
    public String tokens(@RequestParam(name="cardlist", required=true, defaultValue="") String cardlist, @RequestParam(name="matchExact", required=true, defaultValue="") String matchExact, Model model) {
    	boolean _match = matchExact.equals("on");
    	SearchResult sr = tokenResults(cardlist, _match);

    	Collections.sort(sr.tokenResults);
    	Collections.sort(sr.containsCreate);
    	Collections.sort(sr.full_list);
    	
    	model.addAttribute("full_list", sr.full_list);
    	model.addAttribute("errors", sr.errors);
    	model.addAttribute("results", sr.tokenResults.isEmpty() ? null : sr.tokenResults);
    	model.addAttribute("contains_create", sr.containsCreate.isEmpty() ? null : sr.containsCreate);
    	
    	return "tokens";
    }
    
    @PostMapping("/fromurl")
    public String fromurl(@RequestParam(name="deckboxurl", required=true, defaultValue="") String deckboxurl, Model model) {
    	UrlProcessResponse resp=null;
    	try {
			URI parm = new URI(deckboxurl);
			
			String host = parm.getHost().toLowerCase();
			if(host.startsWith("www."))
				host = host.substring(4);
			
			switch(host) {
			case "deckbox.org":
				resp = URL_Processor.fromDeckBox(deckboxurl);
				break;
			case "tappedout.net":
				resp = URL_Processor.fromTappedOut(deckboxurl);
				break;
			case "mtgvault.com":
				resp = URL_Processor.fromMtgVault(deckboxurl);
				break;
				
			case "mtggoldfish.com":
				resp = URL_Processor.fromMtgGoldfish(deckboxurl);
				break;
			}
		} catch (URISyntaxException e1) {
			model.addAttribute("errorlist", new String[] {"The URL entered was invalid."});
			return "error";
		}
    	
    	if(resp == null) {
    		return "unsupported_url";
    	}
    	else if(resp.okay) {
    		return tokens(resp.cardlist, "off", model);
    	}
    	else {
    		model.addAttribute("errorlist", resp.errors);
    		return "error";
    	}
    }
    
    public SearchResult tokenResults(String cardlist, boolean matchExact) {
    	List<String> errors = new ArrayList<String>();
    	List<TokenResult> results = new ArrayList<TokenResult>();
    	List<Card> containsCreate = new ArrayList<Card>();
    	List<ContainsCreateResult> ccResults = new ArrayList<ContainsCreateResult>();
    	List<String> full_list = new ArrayList<String>();
    	List<Card> copyToken = null;
    	
    	try { 		
	    	List<Card> cards = ScryfallDataManager.loadCards();
	    	List<Card> tokens = ScryfallDataManager.loadTokens();
	    	
	    	//Search terms
			ArrayList<String> terms = new ArrayList<String>();
			terms.addAll(Arrays.asList(cardlist.split("\\n")));
			terms.removeIf(p -> p.isEmpty());
			
			for (String term: terms) {
				term = SearchHelper.prepareSearchTerm(term);
				
				if(term.isEmpty() || StringUtils.containsIgnoreCase(term, "sideboard") || StringUtils.containsIgnoreCase(term, "maybeboard"))
					continue;
				
				Card found = SearchHelper.findCardByName(cards, matchExact, term);
				if(found == null) {
					errors.add(term);
				}
				else {					
					full_list.add(term);
					if(found.all_parts != null) {			
						boolean goteem = false;
						for (Iterator<Related_Card> r = found.all_parts.iterator(); r.hasNext();) {
							Related_Card rc = r.next();
							Card token = SearchHelper.findToken(tokens, rc.id);
							if(token != null) {
								goteem = true;
								results = SearchHelper.addTokenAndSources(results, token, found);
							}
						}
						if(!goteem && SearchHelper.oracle_text_contains_create(found)) {
							containsCreate.add(found);
						}
					}
					else if(SearchHelper.oracle_text_contains_create(found)) {
						containsCreate.add(found);
					}
				}				
			}
			
			//Process containsCreate for token guesses			
			for(Card cc : containsCreate) {
				List<Card> guess = null;
				TokenGuess tg = SearchHelper.prepareTokenGuess(cc);
			    
				if(!tg.name.isEmpty()) {
					guess = SearchHelper.findTokensByName(tokens, tg.name, tg.power, tg.toughness);
			    }
				else if(cc.oracle_text.contains("that's a copy of") || cc.oracle_text.contains("that are copies of")) {
					if(copyToken == null)
						copyToken = SearchHelper.findTokensByName(tokens, "Copy", null, null);
					
					guess = copyToken;
				}
		    	
				//Calculated image links
		    	cc.calculated_small  = ScryfallDataManager.getImageApiURL(cc, ImageSize.small, false);
		    	cc.calculated_normal = ScryfallDataManager.getImageApiURL(cc, ImageSize.normal, false);
				
			    ccResults.add(new ContainsCreateResult(cc, guess, ""));
			}
			
			errors.removeIf(p -> p.isEmpty());
    	}
    	catch(Exception e) {  		
    		errors.add("ERROR: " + e.getMessage());
    	}
    	
    	if(errors.size() == 0)
    		errors = null;
    	
    	return new SearchResult(full_list, errors, results, ccResults);
    }
}