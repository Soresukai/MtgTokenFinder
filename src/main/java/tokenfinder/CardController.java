package tokenfinder;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
    	model.addAttribute("exclusions", URL_Processor.SiteExclusions);
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
    		if(deckboxurl.isEmpty())
    			throw new URISyntaxException("", "");
    		
    		resp = URL_Processor.ProcessURL(deckboxurl);			
		} catch (URISyntaxException e1) {
			model.addAttribute("errorlist", new String[] {"The URL entered was invalid."});
			return "error";
		}
    	
    	if(resp == null) {
    		return "unsupported_url";
    	}
    	else if(resp.okay) {
    		model.addAttribute("errorlist", resp.errors);
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
    	
    	try { 		
	    	List<Card> cards = ScryfallDataManager.loadCards();
	    	List<Card> tokens = ScryfallDataManager.loadTokens();
	    	ArrayList<Card> tipcards = ScryfallDataManager.loadTipCards();
	    	
	    	//Search terms
			ArrayList<String> terms = new ArrayList<String>();
			terms.addAll(Arrays.asList(cardlist.split("\\s*\\r?\\n\\s*")));
			
			//Clear obvious list cuts
			terms.removeIf(p -> p.isEmpty());
			terms.removeIf(p -> StringUtils.containsIgnoreCase(p, "sideboard"));
			terms.removeIf(p -> StringUtils.containsIgnoreCase(p, "maybeboard"));
			
			//Trash duplicates
			HashSet<String> uniqueTerms = new HashSet<String> (terms);
			terms.clear();
			terms.addAll(uniqueTerms);
			Collections.sort(terms);
			
			for (String term: terms) {
				term = SearchHelper.prepareSearchTerm(term);
				
				if(term.isEmpty())
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
				
				//Check for tip cards
				if(found != null) {
					if(SearchHelper.oracle_text_contains(found, "experience counter"))
						results = SearchHelper.addTokenAndSources(results, SearchHelper.findTipCard(tipcards, "Experience Counter"), found);
					
					if(SearchHelper.oracle_text_contains(found, "the monarch"))
						results = SearchHelper.addTokenAndSources(results, SearchHelper.findTipCard(tipcards, "The Monarch"), found);
					
					if(SearchHelper.oracle_text_contains(found, "{E}"))
						results = SearchHelper.addTokenAndSources(results, SearchHelper.findTipCard(tipcards, "Energy Reserve"), found);
					
					if(SearchHelper.oracle_text_contains(found, "the city's blessing"))
						results = SearchHelper.addTokenAndSources(results, SearchHelper.findTipCard(tipcards, "City's Blessing"), found);
					
					if(SearchHelper.oracle_text_contains(found, "infect ") || SearchHelper.oracle_text_contains(found, "infect.") || SearchHelper.oracle_text_contains(found, "poison counter"))
						results = SearchHelper.addTokenAndSources(results, SearchHelper.findTipCard(tipcards, "Poison Counter"), found);					
					
					if(SearchHelper.oracle_text_contains(found, "manifest th") || SearchHelper.oracle_text_contains(found, "manifest one") || SearchHelper.oracle_text_contains(found, "manifests "))
						results = SearchHelper.addTokenAndSources(results, SearchHelper.findTipCard(tipcards, "Manifest"), found);
					
					if(SearchHelper.oracle_text_contains(found, "megamorph ") || SearchHelper.oracle_text_contains(found, "morph "))
						results = SearchHelper.addTokenAndSources(results, SearchHelper.findTipCard(tipcards, "Morph"), found);
				}
			}
			
			//Process containsCreate for token guesses	
	    	ArrayList<Card> copyToken = null;
	    	ArrayList<Card> amassToken = null;
	    	
			for(Card cc : containsCreate) {
				ArrayList<Card> guess = new ArrayList<Card>();
				for(TokenGuess tg : SearchHelper.prepareTokenGuess(cc)) {
					if(!tg.name.isEmpty()) {
						guess = SearchHelper.findTokensByName(tokens, tg.name, tg.power, tg.toughness, false);
				    }
					
					//Check for copy tokens
					if(SearchHelper.oracle_text_contains(cc, "that's a copy of") || SearchHelper.oracle_text_contains(cc, "that are copies of")) {
						if(copyToken == null)
							copyToken = SearchHelper.findTokensByName(tokens, "Copy", null, null, true);
						
						guess.addAll(copyToken);
					}
					
					//Check for amass tokens
					if(SearchHelper.oracle_text_contains(cc, "amass ")) {
						if(amassToken == null)
							amassToken = SearchHelper.findTokensByName(tokens, "Zombie Army", "0", "0", true);
						
						boolean hasArmy = false;
						for(Card gc: guess) {
							if(gc.name.equals("Zombie Army"))
								hasArmy = true;
						}
						if(!hasArmy)
							guess.addAll(amassToken);
					}
					
					//Check for emblems
					if(SearchHelper.oracle_text_contains(cc, "emblem ")) {
						//Emblems need to search for each individual card face
						
						//If single-face card
						if(cc.card_faces == null) {
							boolean hasEmblem = false;
							for(Card gc: guess) {
								if(gc.name.equals(cc.name + " Emblem"))
									hasEmblem = true;
							}
							if(!hasEmblem) {
								List<Card> g = SearchHelper.findTokensByName(tokens, cc.name+" Emblem", null, null, true);
								if(g != null) {
									SearchHelper.addTokenAndSources(results, g.get(0), cc);
								}
							}
						}
						//If card is double-faced
						else {
							for(CardFace cf: cc.card_faces) {
								boolean hasEmblem = false;
								for(Card gc: guess) {
									if(gc.name.equals(cf.name + " Emblem"))
										hasEmblem = true;
								}
								if(!hasEmblem) {
									List<Card> g = SearchHelper.findTokensByName(tokens, cc.name+" Emblem", null, null, true);
									if(g != null) {
										SearchHelper.addTokenAndSources(results, g.get(0), cc);
									}
								}
							}
						}
					}
					
					//Calculated image links
			    	cc.calculated_small  = ScryfallDataManager.getImageApiURL(cc, ImageSize.small, false);
			    	cc.calculated_normal = ScryfallDataManager.getImageApiURL(cc, ImageSize.normal, false);
				}
				
		    	//Perform confidence matching
				boolean confidant = false;
				for(Card tokenCheck : guess) {
					if(SearchHelper.cardContainsTokenPhrase(cc, tokenCheck)) {
						confidant = true;
						SearchHelper.addTokenAndSources(results, tokenCheck, cc);
						break;
					}
				}
				
				if(!confidant)
					ccResults.add(new ContainsCreateResult(cc, guess, ""));
			    
			}
			
			errors.removeIf(p -> p.isEmpty());
    	}
    	catch(Exception e) {  		
    		errors.add("ERROR: " + e.getMessage());
    		e.printStackTrace();
    	}
    	
    	if(errors.size() == 0)
    		errors = null;
    	
    	return new SearchResult(full_list, errors, results, ccResults);
    }
}