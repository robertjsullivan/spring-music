package org.cloudfoundry.samples.music.web;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.cloudfoundry.samples.music.domain.Album;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping(value = "/albums")
public class AlbumController {
    @Autowired
    private ObservationRegistry observationRegistry;

    private static final Logger logger = LoggerFactory.getLogger(AlbumController.class);
    private CrudRepository<Album, String> repository;

    @Autowired
    public AlbumController(CrudRepository<Album, String> repository) {
        this.repository = repository;
    }

    @RequestMapping(method = RequestMethod.GET)
    public Iterable<Album> albums() {
        return repository.findAll();
    }

    /*
    @PostMapping("/api/app/data")
    public void handleAppData(@RequestBody Map<String, Object> data) {
        Observation ob = Observation.createNotStarted("some-operation", this.observationRegistry);
        ob.contextualName("printing hello world")
                .observe(() -> {
                    String fooResourceUrl = String.format("http://%s:8080/api/app2/data", service2);
                    ResponseEntity<Void> response = restTemplate.postForEntity(fooResourceUrl, data, Void.class);
                    System.out.println("hello world");
                });
    }
     */

    @RequestMapping(method = RequestMethod.PUT)
    public Album add(@RequestBody @Valid Album album) {
        logger.info("Adding album " + album.getId());
        Observation ob = Observation.createNotStarted("add-album-op", this.observationRegistry);
        ob.contextualName("adding album")
                .observe(() -> repository.save(album));
//        return repository.save(album);
        return null;
    }

    @RequestMapping(method = RequestMethod.POST)
    public Album update(@RequestBody @Valid Album album) {
        logger.info("Updating album " + album.getId());
        return repository.save(album);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public Album getById(@PathVariable String id) {
        logger.info("Getting album " + id);
        return repository.findById(id).orElse(null);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void deleteById(@PathVariable String id) {
        logger.info("Deleting album " + id);
        repository.deleteById(id);
    }
}