package io.tolgee.api.v2.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.tolgee.dtos.BigMetaDto
import io.tolgee.hateoas.key.KeyWithBaseTranslationModel
import io.tolgee.hateoas.key.KeyWithBaseTranslationModelAssembler
import io.tolgee.model.enums.Scope
import io.tolgee.security.ProjectHolder
import io.tolgee.security.authentication.AllowApiAccess
import io.tolgee.security.authorization.RequiresProjectPermissions
import io.tolgee.service.bigMeta.BigMetaService
import io.tolgee.util.Logging
import io.tolgee.util.logger
import io.tolgee.util.traceMeasureTime
import org.springframework.hateoas.CollectionModel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@Suppress("MVCPathVariableInspection")
@RestController
@RequestMapping(
  value = ["/v2/projects/{projectId:\\d+}", "/v2/projects"]
)
@Tag(name = "Big Meta data about the keys in project")
class BigMetaController(
  private val bigMetaService: BigMetaService,
  private val projectHolder: ProjectHolder,
  private val keyWithBaseTranslationModelAssembler: KeyWithBaseTranslationModelAssembler,
) : Logging {
  @PostMapping("/big-meta")
  @Operation(summary = "Stores a bigMeta for a project")
  @RequiresProjectPermissions([Scope.TRANSLATIONS_EDIT])
  @AllowApiAccess
  fun store(@RequestBody @Valid data: BigMetaDto) {
    logger.traceMeasureTime("BigMetaController.store") {
      bigMetaService.store(data, projectHolder.projectEntity)
    }
  }

  @GetMapping("/keys/{id}/big-meta")
  @Operation(summary = "Returns a bigMeta for given key")
  @RequiresProjectPermissions([Scope.TRANSLATIONS_VIEW])
  @AllowApiAccess
  fun getBigMeta(
    @PathVariable("id") id: Long
  ): CollectionModel<KeyWithBaseTranslationModel> {
    val projectId = projectHolder.project.id
    val result = bigMetaService.getCloseKeysWithBaseTranslation(id, projectId)
    return keyWithBaseTranslationModelAssembler.toCollectionModel(result)
  }
}
