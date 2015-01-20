/*
 * Copyright 2005-2013 the original author or authors.
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

package walmart;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.data.neo4j.core.GraphDatabase;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller REST para exposição do serviço de cálculo de custo do menor caminho
 * 
 * @author Darcio
 */
@Controller
@RequestMapping("/shortestpath")
public class ShortestpathController {

	@Autowired
	private ShortestpathCostService spp;

	
	@Autowired
	private LocationRepository locationRepository;

	
	@Autowired
	private GraphDatabase graphDatabase;

	/**
	 * Recebe requisicao REST e invoca serviço shortestpathCost
	 * 
	 * @param name
	 * @return
	 * @throws WalmartException
	 */
	@RequestMapping(value = { "/" }, method = GET)
	@ResponseBody
	public PathCost shortestpath(@RequestParam(required = true) String nomeMapa, @RequestParam(required = true) Float autonomia, @RequestParam(required = true) Float valorcombustivel, @RequestParam(required = true) String locala, @RequestParam(required = true) String localb)
			throws WalmartException {

		// invoca servico shortestpathCost
		PathCost pathCost = spp.shortestpathCost(nomeMapa, autonomia, valorcombustivel, locala, localb);

		return pathCost;
	}

	
	
	/**
	 * Recupera os nomes de todas as malhas viarias contidas no banco 
	 * @return
	 */
	@RequestMapping(value = { "/nomesmapas" }, method = RequestMethod.GET)
	@ResponseBody
	public Set<String> getMalhas(){
		
		Set<String> setMapas = new HashSet<>();
		
		//TODO: Externalizar query
		Result<Map<String, Object>> result = graphDatabase.queryEngine().query("match  (n)  return distinct n.mapa;", null);
		

		Iterator<Map<String, Object>> it = result.iterator();
		
		while(it.hasNext()){
			
			Map<String, Object> map = it.next();
			
			setMapas.add(map.get("n.mapa")+"");
			
		}
		return setMapas;
		
	}
	

	
	/**
	 * Recupera os nomes de todas as malhas viarias contidas no banco 
	 * @return
	 */
	@RequestMapping(value = { "/malhaviaria/{mapa}" }, method = RequestMethod.GET)
	@ResponseBody
	public MalhaViaria getMalha(@PathVariable String mapa){
		
		MalhaViaria malhaViaria = new MalhaViaria();
		
		Map<String, Object> mapParam = new HashMap<>();
		
		mapParam.put("mapa", mapa);
		
		Result<Map<String, Object>> result = graphDatabase.queryEngine().query("match (n {mapa:{mapa}})-[r]-(m) return distinct r", mapParam);
		

		Iterator<Map<String, Object>> it = result.iterator();
		
		while(it.hasNext()){

			malhaViaria.setNomeMapa(mapa);
			
			Map<String, Object> map = it.next();
			
			Relationship relationship = (Relationship) map.get("r");
			
			CaminhoMalha caminhoMalha= new CaminhoMalha();
			
			caminhoMalha.setStartLocation((String) relationship.getStartNode().getProperty("name"));
			caminhoMalha.setEndLocation((String) relationship.getEndNode().getProperty("name"));
			caminhoMalha.setDistance((Double) relationship.getProperty("distance"));

			malhaViaria.getCaminhos().add(caminhoMalha);
			
		}
		
		//TODO: REVISAR!
		if(malhaViaria.getNomeMapa()!=null){
			return malhaViaria;
		}else{
			return null;
		}
	}
	
	
	/**
	 * Recebe requisicao REST e invoca serviço shortestpathCost
	 * 
	 * TODO: a criação do mapa está muito lenta!! Revisar processo
	 * 
	 * @param name
	 * @return
	 * @throws WalmartException
	 */
	@RequestMapping(value = "/malhaviaria", consumes = "application/json", method = RequestMethod.PUT)
	@ResponseBody
	public void criaMalhaViaria( @RequestBody(required = true) MalhaViaria malhaViaria) throws WalmartException {

		/**
		TODO: Construi o servico de criacao de malha de forma bem simples. Creio que não seria admissível manter esta transacao aberta por tanto tempo.
		TODO: Poderia ser criada uma versão mais sofisticada, em batch, conforme recomendacao do manual do Neo4J. 
		TODO: Esta opcao batch nao poderia concorrer com outras transacoes online. A carga de grandes volumes de dados deve ser feita offline, por um DBA. 
		 **/
		Transaction tx = graphDatabase.beginTx();
		
		String nomeMapa = malhaViaria.getNomeMapa();

		//checa se já existe uma malha com o mesmo nome. Lança exceção, caso exista
		checkNameAvailability(nomeMapa);
		
		List<CaminhoMalha> caminhos = malhaViaria.getCaminhos();

		try {
			for (CaminhoMalha caminhoMalha : caminhos) {

				String nameStartLocation = caminhoMalha.getStartLocation();
				String nameEndLocation = caminhoMalha.getEndLocation();

				Location startLocation = mergeLocation(nameStartLocation, nomeMapa);
				Location endLocation = mergeLocation(nameEndLocation, nomeMapa);

				startLocation.connectTo(endLocation, caminhoMalha.getDistance());
				locationRepository.save(startLocation);
			}

			tx.success();
			
		} catch (Exception e) {
			
			throw new WalmartRuntimeException("Erro ao tentar criar malha viária!",e);
			
		} finally {
			tx.close();
		}

	}


	/**
	 * Verifica se já existe uma malha com o mesmo nome.
	 * @param nomeMapa
	 * @throws WalmartException Caso já exista uma malha com este nome.
	 */
	protected void checkNameAvailability(String nomeMapa) throws WalmartException {
		MalhaViaria malhaAtual = getMalha(nomeMapa);
		
		if (malhaAtual!=null){
			throw new WalmartException("Já existe uma malha viária com o nome '"+nomeMapa+"'! Para alteração a malha deve ser recriada!"); 
		}
	}

	
	/**
	 * Recupera os nomes de todas as malhas viarias contidas no banco 
	 * @return
	 */
	@RequestMapping(value = { "/malhaviaria/{mapa}" }, method = RequestMethod.DELETE)
	@ResponseBody
	public void deleteMalha(@PathVariable String mapa){
		
		
		Map<String, Object> mapParam = new HashMap<>();
		
		mapParam.put("mapa", mapa);
		
		//TODO: Externalizar queries
		graphDatabase.queryEngine().query("MATCH (n {mapa:{mapa}})-[r]-() DELETE n, r", mapParam);
		
	}
	
	
	
	
	
	
	/**
	 * Encontra ou cria o registro de location
	 * @param locationName
	 * @param mapName
	 * @return
	 */
	private Location mergeLocation(String locationName, String mapName) {

		Location location = locationRepository.findByNameAndMapa(locationName, mapName);

		if (location == null) {
			location = new Location();

			location.setMapa(mapName);
			location.setName(locationName);

			location = locationRepository.save(location);
		}

		return location;
	}

}
