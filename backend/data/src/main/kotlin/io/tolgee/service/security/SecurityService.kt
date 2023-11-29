package io.tolgee.service.security

import io.tolgee.constants.Message
import io.tolgee.dtos.ComputedPermissionDto
import io.tolgee.dtos.cacheable.ApiKeyDto
import io.tolgee.dtos.cacheable.UserAccountDto
import io.tolgee.exceptions.LanguageNotPermittedException
import io.tolgee.exceptions.NotFoundException
import io.tolgee.exceptions.PermissionException
import io.tolgee.model.ApiKey
import io.tolgee.model.Project
import io.tolgee.model.UserAccount
import io.tolgee.model.enums.Scope
import io.tolgee.model.translation.Translation
import io.tolgee.repository.KeyRepository
import io.tolgee.security.authentication.AuthenticationFacade
import io.tolgee.service.LanguageService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.Serializable

@Service
class SecurityService(
  private val authenticationFacade: AuthenticationFacade,
  private val languageService: LanguageService,
  private val keyRepository: KeyRepository
) {

  @set:Autowired
  lateinit var apiKeyService: ApiKeyService

  @set:Autowired
  lateinit var permissionService: PermissionService

  @set:Autowired
  lateinit var userAccountService: UserAccountService

  fun checkAnyProjectPermission(projectId: Long) {
    if (
      getProjectPermissionScopes(projectId).isNullOrEmpty() &&
      !isCurrentUserServerAdmin()
    )
      throw PermissionException()
  }

  fun checkProjectPermission(projectId: Long, requiredScopes: Scope, apiKey: ApiKeyDto) {
    checkProjectPermission(listOf(requiredScopes), apiKey)
  }

  private fun checkProjectPermission(requiredScopes: List<Scope>, apiKey: ApiKeyDto) {
    this.checkApiKeyScopes(requiredScopes, apiKey)
  }

  fun checkProjectPermissionNoApiKey(
    projectId: Long,
    requiredScope: Scope,
    userAccountDto: UserAccountDto
  ) {
    if (isUserAdmin(userAccountDto)) {
      return
    }

    val allowedScopes = getProjectPermissionScopes(projectId, userAccountDto.id)
      ?: throw PermissionException(Message.USER_HAS_NO_PROJECT_ACCESS)

    checkPermission(requiredScope, allowedScopes)
  }

  private fun checkPermission(
    requiredScope: Scope,
    allowedScopes: Array<Scope>
  ) {
    if (!allowedScopes.contains(requiredScope)) {
      throw PermissionException(
        Message.OPERATION_NOT_PERMITTED,
        listOf(requiredScope.value) as List<Serializable>
      )
    }
  }

  fun checkProjectPermission(projectId: Long, requiredPermission: Scope) {
    // Always check for the current user even if we're using an API key for security reasons.
    // This prevents improper preservation of permissions.
    checkProjectPermissionNoApiKey(projectId, requiredPermission, activeUser)

    val apiKey = activeApiKey ?: return
    checkProjectPermission(projectId, requiredPermission, apiKey)
  }

  fun checkLanguageViewPermissionByTag(projectId: Long, languageTags: Collection<String>) {
    checkProjectPermission(projectId, Scope.TRANSLATIONS_VIEW)
    checkLanguagePermissionByTag(
      projectId,
      languageTags
    ) { data, languageIds -> data.checkViewPermitted(*languageIds.toLongArray()) }
  }

  fun checkLanguageTranslatePermissionByTag(projectId: Long, languageTags: Collection<String>) {
    checkProjectPermission(projectId, Scope.TRANSLATIONS_EDIT)
    checkLanguagePermissionByTag(
      projectId,
      languageTags
    ) { data, languageIds -> data.checkTranslatePermitted(*languageIds.toLongArray()) }
  }

  fun checkStateEditPermissionByTag(projectId: Long, languageTags: Collection<String>) {
    checkProjectPermission(projectId, Scope.TRANSLATIONS_STATE_EDIT)
    checkLanguagePermissionByTag(
      projectId,
      languageTags
    ) { data, languageIds -> data.checkTranslatePermitted(*languageIds.toLongArray()) }
  }

  fun checkLanguageViewPermission(projectId: Long, languageIds: Collection<Long>) {
    checkProjectPermission(projectId, Scope.TRANSLATIONS_VIEW)
    checkLanguagePermission(
      projectId,
    ) { data -> data.checkViewPermitted(*languageIds.toLongArray()) }
  }

  fun checkLanguageTranslatePermission(projectId: Long, languageIds: Collection<Long>) {
    checkProjectPermission(projectId, Scope.TRANSLATIONS_EDIT)
    checkLanguagePermission(
      projectId,
    ) { data -> data.checkTranslatePermitted(*languageIds.toLongArray()) }
  }

  fun checkLanguageStateChangePermission(projectId: Long, languageIds: Collection<Long>) {
    checkProjectPermission(projectId, Scope.TRANSLATIONS_STATE_EDIT)
    checkLanguagePermission(
      projectId,
    ) { data -> data.checkStateChangePermitted(*languageIds.toLongArray()) }
  }

  fun filterViewPermissionByTag(projectId: Long, languageTags: Collection<String>): Set<String> {
    try {
      checkLanguageViewPermissionByTag(projectId, languageTags)
    } catch (e: LanguageNotPermittedException) {
      return languageTags.toSet() - e.languageTags.orEmpty().toSet()
    }
    return languageTags.toSet()
  }

  private fun checkLanguagePermission(
    projectId: Long,
    permissionCheckFn: (data: ComputedPermissionDto) -> Unit
  ) {
    if (isCurrentUserServerAdmin()) {
      return
    }
    val usersPermission = permissionService.getProjectPermissionData(
      projectId,
      authenticationFacade.authenticatedUser.id
    )
    permissionCheckFn(usersPermission.computedPermissions)
  }

  private fun checkLanguagePermissionByTag(
    projectId: Long,
    languageTags: Collection<String>,
    fn: (data: ComputedPermissionDto, languageIds: Collection<Long>) -> Unit
  ) {
    val languageIds = languageService.getLanguageIdsByTags(projectId, languageTags)
    try {
      val usersPermission = permissionService.getProjectPermissionData(
        projectId,
        authenticationFacade.authenticatedUser.id
      )
      fn(usersPermission.computedPermissions, languageIds.values.map { it.id })
    } catch (e: LanguageNotPermittedException) {
      throw LanguageNotPermittedException(
        e.languageIds,
        e.languageIds.mapNotNull { languageId -> languageIds.entries.find { it.value.id == languageId }?.key }
      )
    }
  }

  fun checkLanguageTranslatePermission(translation: Translation) {
    val language = translation.language
    checkLanguageTranslatePermission(language.project.id, listOf(language.id))
  }

  fun checkStateChangePermission(translation: Translation) {
    val language = translation.language
    checkLanguageStateChangePermission(language.project.id, listOf(language.id))
  }

  fun checkLanguageTagPermissions(tags: Set<String>, projectId: Long) {
    val languages = languageService.findByTags(tags, projectId)
    this.checkLanguageTranslatePermission(projectId, languages.map { it.id })
  }

  fun checkApiKeyScopes(scopes: Set<Scope>, project: Project?, user: UserAccount? = null) {
    try {
      val availableScopes = apiKeyService.getAvailableScopes(user?.id ?: activeUser.id, project!!)
      val userCanSelectTheScopes = availableScopes.toList().containsAll(scopes)
      if (!userCanSelectTheScopes) {
        throw PermissionException()
      }
    } catch (e: NotFoundException) {
      throw PermissionException()
    }
  }

  fun checkBigMetaUploadPermission(projectId: Long) {
    checkProjectPermission(projectId, Scope.TRANSLATIONS_EDIT)
  }

  fun fixInvalidApiKeyWhenRequired(apiKey: ApiKey) {
    val oldSize = apiKey.scopesEnum.size
    apiKey.scopesEnum.removeIf {
      getProjectPermissionScopes(
        apiKey.project.id,
        apiKey.userAccount.id
      )?.contains(it) != true
    }
    if (oldSize != apiKey.scopesEnum.size) {
      apiKeyService.save(apiKey)
    }
  }

  fun checkApiKeyScopes(scopes: Set<Scope>, apiKey: ApiKeyDto) {
    checkApiKeyScopes(apiKey) { expandedScopes ->
      if (!expandedScopes.toList().containsAll(scopes)) {
        throw PermissionException()
      }
    }
  }

  fun checkApiKeyScopes(scopes: Collection<Scope>, apiKey: ApiKeyDto) {
    checkApiKeyScopes(apiKey) { expandedScopes ->
      val hasRequiredPermission = scopes.all { expandedScopes.contains(it) }
      if (!hasRequiredPermission) {
        throw PermissionException()
      }
    }
  }

  private fun checkApiKeyScopes(apiKey: ApiKeyDto, checkFn: (expandedScopes: Array<Scope>) -> Unit) {
    val expandedScopes = Scope.expand(apiKey.scopes)
    checkFn(expandedScopes)
  }

  fun checkScreenshotsUploadPermission(projectId: Long) {
    if (authenticationFacade.isProjectApiKeyAuth) {
      checkApiKeyScopes(setOf(Scope.SCREENSHOTS_UPLOAD), authenticationFacade.projectApiKey)
    }
    checkProjectPermission(projectId, Scope.SCREENSHOTS_UPLOAD)
  }

  fun checkUserIsServerAdmin() {
    if (authenticationFacade.authenticatedUser.role != UserAccount.Role.ADMIN) {
      throw PermissionException()
    }
  }

  fun getProjectPermissionScopes(projectId: Long, userId: Long = activeUser.id): Array<Scope>? {
    return permissionService.getProjectPermissionScopes(projectId, userId)
  }

  fun checkKeyIdsExistAndIsFromProject(keyIds: List<Long>, projectId: Long) {
    val projectIds = keyRepository.getProjectIdsForKeyIds(keyIds)

    if (projectIds.size != keyIds.size) {
      throw NotFoundException(Message.KEY_NOT_FOUND)
    }

    val firstProjectId = projectIds[0]

    if (projectIds.any { it != firstProjectId }) {
      throw PermissionException(Message.MULTIPLE_PROJECTS_NOT_SUPPORTED)
    }

    if (firstProjectId != projectId) {
      throw PermissionException(Message.KEY_NOT_FROM_PROJECT)
    }
  }

  private fun isCurrentUserServerAdmin(): Boolean {
    return isUserAdmin(activeUser)
  }

  private fun isUserAdmin(user: UserAccountDto): Boolean {
    return user.role == UserAccount.Role.ADMIN
  }

  private val activeUser: UserAccountDto
    get() = authenticationFacade.authenticatedUserOrNull ?: throw PermissionException()

  private val activeApiKey: ApiKeyDto?
    get() = if (authenticationFacade.isProjectApiKeyAuth) authenticationFacade.projectApiKey else null
}
