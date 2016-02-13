/**
 * Generated by apidoc - http://www.apidoc.me
 * Service version: 0.0.3
 * apidoc:0.11.8 http://www.apidoc.me/flow/delta/0.0.3/anorm_2_x_parsers
 */
import anorm._

package io.flow.delta.v0.anorm.parsers {

  import io.flow.delta.v0.anorm.conversions.Json._

  object EventAction {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(s"$prefix${sep}name")

    def parser(name: String = "event_action"): RowParser[io.flow.delta.v0.models.EventAction] = {
      SqlParser.str(name) map {
        case value => io.flow.delta.v0.models.EventAction(value)
      }
    }

  }

  object Publication {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(s"$prefix${sep}name")

    def parser(name: String = "publication"): RowParser[io.flow.delta.v0.models.Publication] = {
      SqlParser.str(name) map {
        case value => io.flow.delta.v0.models.Publication(value)
      }
    }

  }

  object Role {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(s"$prefix${sep}name")

    def parser(name: String = "role"): RowParser[io.flow.delta.v0.models.Role] = {
      SqlParser.str(name) map {
        case value => io.flow.delta.v0.models.Role(value)
      }
    }

  }

  object Scms {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(s"$prefix${sep}name")

    def parser(name: String = "scms"): RowParser[io.flow.delta.v0.models.Scms] = {
      SqlParser.str(name) map {
        case value => io.flow.delta.v0.models.Scms(value)
      }
    }

  }

  object Visibility {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(s"$prefix${sep}name")

    def parser(name: String = "visibility"): RowParser[io.flow.delta.v0.models.Visibility] = {
      SqlParser.str(name) map {
        case value => io.flow.delta.v0.models.Visibility(value)
      }
    }

  }

  object Event {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      action = s"$prefix${sep}action",
      summary = s"$prefix${sep}summary",
      error = s"$prefix${sep}error"
    )

    def parser(
      id: String = "id",
      action: String = "action",
      summary: String = "summary",
      error: String = "error"
    ): RowParser[io.flow.delta.v0.models.Event] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.EventAction.parser(action) ~
      SqlParser.str(summary) ~
      SqlParser.str(error).? map {
        case id ~ action ~ summary ~ error => {
          io.flow.delta.v0.models.Event(
            id = id,
            action = action,
            summary = summary,
            error = error
          )
        }
      }
    }

  }

  object GithubAuthenticationForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      code = s"$prefix${sep}code"
    )

    def parser(
      code: String = "code"
    ): RowParser[io.flow.delta.v0.models.GithubAuthenticationForm] = {
      SqlParser.str(code) map {
        case code => {
          io.flow.delta.v0.models.GithubAuthenticationForm(
            code = code
          )
        }
      }
    }

  }

  object GithubUser {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      userPrefix = s"$prefix${sep}user",
      githubUserId = s"$prefix${sep}github_user_id",
      login = s"$prefix${sep}login"
    )

    def parser(
      id: String = "id",
      userPrefix: String = "user",
      githubUserId: String = "github_user_id",
      login: String = "login"
    ): RowParser[io.flow.delta.v0.models.GithubUser] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.Reference.parserWithPrefix(userPrefix) ~
      SqlParser.long(githubUserId) ~
      SqlParser.str(login) map {
        case id ~ user ~ githubUserId ~ login => {
          io.flow.delta.v0.models.GithubUser(
            id = id,
            user = user,
            githubUserId = githubUserId,
            login = login
          )
        }
      }
    }

  }

  object GithubUserForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      userId = s"$prefix${sep}user_id",
      githubUserId = s"$prefix${sep}github_user_id",
      login = s"$prefix${sep}login"
    )

    def parser(
      userId: String = "user_id",
      githubUserId: String = "github_user_id",
      login: String = "login"
    ): RowParser[io.flow.delta.v0.models.GithubUserForm] = {
      SqlParser.str(userId) ~
      SqlParser.long(githubUserId) ~
      SqlParser.str(login) map {
        case userId ~ githubUserId ~ login => {
          io.flow.delta.v0.models.GithubUserForm(
            userId = userId,
            githubUserId = githubUserId,
            login = login
          )
        }
      }
    }

  }

  object GithubWebhook {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id"
    )

    def parser(
      id: String = "id"
    ): RowParser[io.flow.delta.v0.models.GithubWebhook] = {
      SqlParser.long(id) map {
        case id => {
          io.flow.delta.v0.models.GithubWebhook(
            id = id
          )
        }
      }
    }

  }

  object Image {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      projectPrefix = s"$prefix${sep}project",
      name = s"$prefix${sep}name",
      version = s"$prefix${sep}version"
    )

    def parser(
      id: String = "id",
      projectPrefix: String = "project",
      name: String = "name",
      version: String = "version"
    ): RowParser[io.flow.delta.v0.models.Image] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.ProjectSummary.parserWithPrefix(projectPrefix) ~
      SqlParser.str(name) ~
      SqlParser.str(version) map {
        case id ~ project ~ name ~ version => {
          io.flow.delta.v0.models.Image(
            id = id,
            project = project,
            name = name,
            version = version
          )
        }
      }
    }

  }

  object ImageForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      projectId = s"$prefix${sep}project_id",
      name = s"$prefix${sep}name",
      version = s"$prefix${sep}version"
    )

    def parser(
      projectId: String = "project_id",
      name: String = "name",
      version: String = "version"
    ): RowParser[io.flow.delta.v0.models.ImageForm] = {
      SqlParser.str(projectId) ~
      SqlParser.str(name) ~
      SqlParser.str(version) map {
        case projectId ~ name ~ version => {
          io.flow.delta.v0.models.ImageForm(
            projectId = projectId,
            name = name,
            version = version
          )
        }
      }
    }

  }

  object Item {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      organizationPrefix = s"$prefix${sep}organization",
      visibility = s"$prefix${sep}visibility",
      summaryPrefix = s"$prefix${sep}summary",
      label = s"$prefix${sep}label",
      description = s"$prefix${sep}description"
    )

    def parser(
      id: String = "id",
      organizationPrefix: String = "organization",
      visibility: String = "visibility",
      summaryPrefix: String = "summary",
      label: String = "label",
      description: String = "description"
    ): RowParser[io.flow.delta.v0.models.Item] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.OrganizationSummary.parserWithPrefix(organizationPrefix) ~
      io.flow.delta.v0.anorm.parsers.Visibility.parser(visibility) ~
      io.flow.delta.v0.anorm.parsers.ItemSummary.parserWithPrefix(summaryPrefix) ~
      SqlParser.str(label) ~
      SqlParser.str(description).? map {
        case id ~ organization ~ visibility ~ summary ~ label ~ description => {
          io.flow.delta.v0.models.Item(
            id = id,
            organization = organization,
            visibility = visibility,
            summary = summary,
            label = label,
            description = description
          )
        }
      }
    }

  }

  object Membership {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      userPrefix = s"$prefix${sep}user",
      organizationPrefix = s"$prefix${sep}organization",
      role = s"$prefix${sep}role"
    )

    def parser(
      id: String = "id",
      userPrefix: String = "user",
      organizationPrefix: String = "organization",
      role: String = "role"
    ): RowParser[io.flow.delta.v0.models.Membership] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.UserSummary.parserWithPrefix(userPrefix) ~
      io.flow.delta.v0.anorm.parsers.OrganizationSummary.parserWithPrefix(organizationPrefix) ~
      io.flow.delta.v0.anorm.parsers.Role.parser(role) map {
        case id ~ user ~ organization ~ role => {
          io.flow.delta.v0.models.Membership(
            id = id,
            user = user,
            organization = organization,
            role = role
          )
        }
      }
    }

  }

  object MembershipForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      userId = s"$prefix${sep}user_id",
      organization = s"$prefix${sep}organization",
      role = s"$prefix${sep}role"
    )

    def parser(
      userId: String = "user_id",
      organization: String = "organization",
      role: String = "role"
    ): RowParser[io.flow.delta.v0.models.MembershipForm] = {
      SqlParser.str(userId) ~
      SqlParser.str(organization) ~
      io.flow.delta.v0.anorm.parsers.Role.parser(role) map {
        case userId ~ organization ~ role => {
          io.flow.delta.v0.models.MembershipForm(
            userId = userId,
            organization = organization,
            role = role
          )
        }
      }
    }

  }

  object Organization {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      userPrefix = s"$prefix${sep}user"
    )

    def parser(
      id: String = "id",
      userPrefix: String = "user"
    ): RowParser[io.flow.delta.v0.models.Organization] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.UserSummary.parserWithPrefix(userPrefix) map {
        case id ~ user => {
          io.flow.delta.v0.models.Organization(
            id = id,
            user = user
          )
        }
      }
    }

  }

  object OrganizationForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id"
    )

    def parser(
      id: String = "id"
    ): RowParser[io.flow.delta.v0.models.OrganizationForm] = {
      SqlParser.str(id) map {
        case id => {
          io.flow.delta.v0.models.OrganizationForm(
            id = id
          )
        }
      }
    }

  }

  object OrganizationSummary {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id"
    )

    def parser(
      id: String = "id"
    ): RowParser[io.flow.delta.v0.models.OrganizationSummary] = {
      SqlParser.str(id) map {
        case id => {
          io.flow.delta.v0.models.OrganizationSummary(
            id = id
          )
        }
      }
    }

  }

  object Project {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      organizationPrefix = s"$prefix${sep}organization",
      userPrefix = s"$prefix${sep}user",
      visibility = s"$prefix${sep}visibility",
      scms = s"$prefix${sep}scms",
      name = s"$prefix${sep}name",
      uri = s"$prefix${sep}uri"
    )

    def parser(
      id: String = "id",
      organizationPrefix: String = "organization",
      userPrefix: String = "user",
      visibility: String = "visibility",
      scms: String = "scms",
      name: String = "name",
      uri: String = "uri"
    ): RowParser[io.flow.delta.v0.models.Project] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.OrganizationSummary.parserWithPrefix(organizationPrefix) ~
      io.flow.delta.v0.anorm.parsers.Reference.parserWithPrefix(userPrefix) ~
      io.flow.delta.v0.anorm.parsers.Visibility.parser(visibility) ~
      io.flow.delta.v0.anorm.parsers.Scms.parser(scms) ~
      SqlParser.str(name) ~
      SqlParser.str(uri) map {
        case id ~ organization ~ user ~ visibility ~ scms ~ name ~ uri => {
          io.flow.delta.v0.models.Project(
            id = id,
            organization = organization,
            user = user,
            visibility = visibility,
            scms = scms,
            name = name,
            uri = uri
          )
        }
      }
    }

  }

  object ProjectForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      organization = s"$prefix${sep}organization",
      name = s"$prefix${sep}name",
      visibility = s"$prefix${sep}visibility",
      scms = s"$prefix${sep}scms",
      uri = s"$prefix${sep}uri"
    )

    def parser(
      organization: String = "organization",
      name: String = "name",
      visibility: String = "visibility",
      scms: String = "scms",
      uri: String = "uri"
    ): RowParser[io.flow.delta.v0.models.ProjectForm] = {
      SqlParser.str(organization) ~
      SqlParser.str(name) ~
      io.flow.delta.v0.anorm.parsers.Visibility.parser(visibility) ~
      io.flow.delta.v0.anorm.parsers.Scms.parser(scms) ~
      SqlParser.str(uri) map {
        case organization ~ name ~ visibility ~ scms ~ uri => {
          io.flow.delta.v0.models.ProjectForm(
            organization = organization,
            name = name,
            visibility = visibility,
            scms = scms,
            uri = uri
          )
        }
      }
    }

  }

  object ProjectState {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      expectedPrefix = s"$prefix${sep}expected",
      actualPrefix = s"$prefix${sep}actual"
    )

    def parser(
      expectedPrefix: String = "expected",
      actualPrefix: String = "actual"
    ): RowParser[io.flow.delta.v0.models.ProjectState] = {
      io.flow.delta.v0.anorm.parsers.State.parserWithPrefix(expectedPrefix).? ~
      io.flow.delta.v0.anorm.parsers.State.parserWithPrefix(actualPrefix).? map {
        case expected ~ actual => {
          io.flow.delta.v0.models.ProjectState(
            expected = expected,
            actual = actual
          )
        }
      }
    }

  }

  object ProjectSummary {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      organizationPrefix = s"$prefix${sep}organization",
      name = s"$prefix${sep}name",
      uri = s"$prefix${sep}uri"
    )

    def parser(
      id: String = "id",
      organizationPrefix: String = "organization",
      name: String = "name",
      uri: String = "uri"
    ): RowParser[io.flow.delta.v0.models.ProjectSummary] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.OrganizationSummary.parserWithPrefix(organizationPrefix) ~
      SqlParser.str(name) ~
      SqlParser.str(uri) map {
        case id ~ organization ~ name ~ uri => {
          io.flow.delta.v0.models.ProjectSummary(
            id = id,
            organization = organization,
            name = name,
            uri = uri
          )
        }
      }
    }

  }

  object Reference {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id"
    )

    def parser(
      id: String = "id"
    ): RowParser[io.flow.delta.v0.models.Reference] = {
      SqlParser.str(id) map {
        case id => {
          io.flow.delta.v0.models.Reference(
            id = id
          )
        }
      }
    }

  }

  object Repository {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      name = s"$prefix${sep}name",
      visibility = s"$prefix${sep}visibility",
      uri = s"$prefix${sep}uri"
    )

    def parser(
      name: String = "name",
      visibility: String = "visibility",
      uri: String = "uri"
    ): RowParser[io.flow.delta.v0.models.Repository] = {
      SqlParser.str(name) ~
      io.flow.delta.v0.anorm.parsers.Visibility.parser(visibility) ~
      SqlParser.str(uri) map {
        case name ~ visibility ~ uri => {
          io.flow.delta.v0.models.Repository(
            name = name,
            visibility = visibility,
            uri = uri
          )
        }
      }
    }

  }

  object State {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      timestamp = s"$prefix${sep}timestamp",
      versions = s"$prefix${sep}versions"
    )

    def parser(
      timestamp: String = "timestamp",
      versions: String = "versions"
    ): RowParser[io.flow.delta.v0.models.State] = {
      SqlParser.get[_root_.org.joda.time.DateTime](timestamp) ~
      SqlParser.get[Seq[io.flow.delta.v0.models.Version]](versions) map {
        case timestamp ~ versions => {
          io.flow.delta.v0.models.State(
            timestamp = timestamp,
            versions = versions
          )
        }
      }
    }

  }

  object StateForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      versions = s"$prefix${sep}versions"
    )

    def parser(
      versions: String = "versions"
    ): RowParser[io.flow.delta.v0.models.StateForm] = {
      SqlParser.get[Seq[io.flow.delta.v0.models.Version]](versions) map {
        case versions => {
          io.flow.delta.v0.models.StateForm(
            versions = versions
          )
        }
      }
    }

  }

  object Subscription {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      userPrefix = s"$prefix${sep}user",
      publication = s"$prefix${sep}publication"
    )

    def parser(
      id: String = "id",
      userPrefix: String = "user",
      publication: String = "publication"
    ): RowParser[io.flow.delta.v0.models.Subscription] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.Reference.parserWithPrefix(userPrefix) ~
      io.flow.delta.v0.anorm.parsers.Publication.parser(publication) map {
        case id ~ user ~ publication => {
          io.flow.delta.v0.models.Subscription(
            id = id,
            user = user,
            publication = publication
          )
        }
      }
    }

  }

  object SubscriptionForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      userId = s"$prefix${sep}user_id",
      publication = s"$prefix${sep}publication"
    )

    def parser(
      userId: String = "user_id",
      publication: String = "publication"
    ): RowParser[io.flow.delta.v0.models.SubscriptionForm] = {
      SqlParser.str(userId) ~
      io.flow.delta.v0.anorm.parsers.Publication.parser(publication) map {
        case userId ~ publication => {
          io.flow.delta.v0.models.SubscriptionForm(
            userId = userId,
            publication = publication
          )
        }
      }
    }

  }

  object Token {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      userPrefix = s"$prefix${sep}user",
      masked = s"$prefix${sep}masked",
      cleartext = s"$prefix${sep}cleartext",
      description = s"$prefix${sep}description"
    )

    def parser(
      id: String = "id",
      userPrefix: String = "user",
      masked: String = "masked",
      cleartext: String = "cleartext",
      description: String = "description"
    ): RowParser[io.flow.delta.v0.models.Token] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.Reference.parserWithPrefix(userPrefix) ~
      SqlParser.str(masked) ~
      SqlParser.str(cleartext).? ~
      SqlParser.str(description).? map {
        case id ~ user ~ masked ~ cleartext ~ description => {
          io.flow.delta.v0.models.Token(
            id = id,
            user = user,
            masked = masked,
            cleartext = cleartext,
            description = description
          )
        }
      }
    }

  }

  object TokenForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      userId = s"$prefix${sep}user_id",
      description = s"$prefix${sep}description"
    )

    def parser(
      userId: String = "user_id",
      description: String = "description"
    ): RowParser[io.flow.delta.v0.models.TokenForm] = {
      SqlParser.str(userId) ~
      SqlParser.str(description).? map {
        case userId ~ description => {
          io.flow.delta.v0.models.TokenForm(
            userId = userId,
            description = description
          )
        }
      }
    }

  }

  object UserForm {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      email = s"$prefix${sep}email",
      namePrefix = s"$prefix${sep}name"
    )

    def parser(
      email: String = "email",
      namePrefix: String = "name"
    ): RowParser[io.flow.delta.v0.models.UserForm] = {
      SqlParser.str(email).? ~
      io.flow.common.v0.anorm.parsers.Name.parserWithPrefix(namePrefix).? map {
        case email ~ name => {
          io.flow.delta.v0.models.UserForm(
            email = email,
            name = name
          )
        }
      }
    }

  }

  object UserIdentifier {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      userPrefix = s"$prefix${sep}user",
      value = s"$prefix${sep}value"
    )

    def parser(
      id: String = "id",
      userPrefix: String = "user",
      value: String = "value"
    ): RowParser[io.flow.delta.v0.models.UserIdentifier] = {
      SqlParser.str(id) ~
      io.flow.delta.v0.anorm.parsers.Reference.parserWithPrefix(userPrefix) ~
      SqlParser.str(value) map {
        case id ~ user ~ value => {
          io.flow.delta.v0.models.UserIdentifier(
            id = id,
            user = user,
            value = value
          )
        }
      }
    }

  }

  object UserSummary {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      id = s"$prefix${sep}id",
      email = s"$prefix${sep}email",
      namePrefix = s"$prefix${sep}name"
    )

    def parser(
      id: String = "id",
      email: String = "email",
      namePrefix: String = "name"
    ): RowParser[io.flow.delta.v0.models.UserSummary] = {
      SqlParser.str(id) ~
      SqlParser.str(email).? ~
      io.flow.common.v0.anorm.parsers.Name.parserWithPrefix(namePrefix) map {
        case id ~ email ~ name => {
          io.flow.delta.v0.models.UserSummary(
            id = id,
            email = email,
            name = name
          )
        }
      }
    }

  }

  object UsernamePassword {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      username = s"$prefix${sep}username",
      password = s"$prefix${sep}password"
    )

    def parser(
      username: String = "username",
      password: String = "password"
    ): RowParser[io.flow.delta.v0.models.UsernamePassword] = {
      SqlParser.str(username) ~
      SqlParser.str(password).? map {
        case username ~ password => {
          io.flow.delta.v0.models.UsernamePassword(
            username = username,
            password = password
          )
        }
      }
    }

  }

  object Version {

    def parserWithPrefix(prefix: String, sep: String = "_") = parser(
      name = s"$prefix${sep}name",
      instances = s"$prefix${sep}instances"
    )

    def parser(
      name: String = "name",
      instances: String = "instances"
    ): RowParser[io.flow.delta.v0.models.Version] = {
      SqlParser.str(name) ~
      SqlParser.long(instances) map {
        case name ~ instances => {
          io.flow.delta.v0.models.Version(
            name = name,
            instances = instances
          )
        }
      }
    }

  }

  object ItemSummary {

    def parserWithPrefix(prefix: String, sep: String = "_") = {
      io.flow.delta.v0.anorm.parsers.ProjectSummary.parserWithPrefix(prefix, sep)
    }

    def parser() = {
      io.flow.delta.v0.anorm.parsers.ProjectSummary.parser()
    }

  }

}