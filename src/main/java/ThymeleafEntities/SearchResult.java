package ThymeleafEntities;

import java.util.ArrayList;
import java.util.List;

public class SearchResult {
	public List<String> errors;
	public final List<String> full_list;
	public final List<TokenResult> tokenResults;
	public final List<ContainsCreateResult> containsCreate;
	
	public SearchResult() {
		this.full_list = new ArrayList<>();
		this.errors = new ArrayList<>();
		this.tokenResults = new ArrayList<>();
		this.containsCreate = new ArrayList<>();
	}
	
	public SearchResult(List<String> full_list, List<String> errors, List<TokenResult> tokenResults, List<ContainsCreateResult> containsCreate) {
		this.full_list = full_list;
		this.errors = errors;
		this.tokenResults = tokenResults;
		this.containsCreate = containsCreate;
	}
}
