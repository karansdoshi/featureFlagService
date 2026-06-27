package com.digitalocean.featureflags.api;

import com.digitalocean.featureflags.api.dto.CreateFlagRequest;
import com.digitalocean.featureflags.api.dto.EvaluateRequest;
import com.digitalocean.featureflags.api.dto.EvaluateResponse;
import com.digitalocean.featureflags.api.dto.FlagResponse;
import com.digitalocean.featureflags.api.dto.OverrideRequest;
import com.digitalocean.featureflags.api.dto.UpdateFlagRequest;
import com.digitalocean.featureflags.domain.EvaluationContext;
import com.digitalocean.featureflags.domain.EvaluationResult;
import com.digitalocean.featureflags.service.EvaluationService;
import com.digitalocean.featureflags.service.FlagService;
import com.digitalocean.featureflags.service.OverrideService;
import com.digitalocean.featureflags.storage.FlagRecord;
import com.digitalocean.featureflags.storage.FlagWrite;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flags")
public class FlagController {

    private final FlagService flagService;
    private final EvaluationService evaluationService;
    private final OverrideService overrideService;

    public FlagController(FlagService flagService,
                          EvaluationService evaluationService,
                          OverrideService overrideService) {
        this.flagService = flagService;
        this.evaluationService = evaluationService;
        this.overrideService = overrideService;
    }

    @PostMapping
    public ResponseEntity<FlagResponse> create(@Valid @RequestBody CreateFlagRequest request) {
        FlagWrite write = new FlagWrite(
                request.name(),
                request.defaultState(),
                request.rolloutPercentage(),
                FlagMapper.toDomainRules(request.rules()));
        FlagRecord created = flagService.create(write);
        return ResponseEntity
                .created(URI.create("/api/flags/" + created.name()))
                .body(FlagMapper.toResponse(created));
    }

    @GetMapping
    public List<FlagResponse> list() {
        return flagService.list().stream().map(FlagMapper::toResponse).toList();
    }

    @GetMapping("/{name}")
    public FlagResponse get(@PathVariable String name) {
        return FlagMapper.toResponse(flagService.get(name));
    }

    @PutMapping("/{name}")
    public FlagResponse update(@PathVariable String name, @Valid @RequestBody UpdateFlagRequest request) {
        FlagWrite write = new FlagWrite(
                name,
                request.defaultState(),
                request.rolloutPercentage(),
                FlagMapper.toDomainRules(request.rules()));
        return FlagMapper.toResponse(flagService.update(name, write));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        flagService.delete(name);
    }

    @PostMapping("/{name}/evaluate")
    public EvaluateResponse evaluate(@PathVariable String name, @Valid @RequestBody EvaluateRequest request) {
        EvaluationResult result = evaluationService.evaluate(
                name,
                new EvaluationContext(request.userId(), request.subscriptionTier(), request.region()));
        return new EvaluateResponse(result.flag(), result.enabled(), result.reason());
    }

    @PutMapping("/{name}/overrides/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setOverride(@PathVariable String name,
                            @PathVariable String userId,
                            @Valid @RequestBody OverrideRequest request) {
        overrideService.set(name, userId, request.state());
    }

    @DeleteMapping("/{name}/overrides/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOverride(@PathVariable String name, @PathVariable String userId) {
        overrideService.remove(name, userId);
    }
}
