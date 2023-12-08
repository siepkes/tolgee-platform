import { FunctionComponent, useState } from 'react';
import { Box, Grid, Typography } from '@mui/material';
import { T, useTranslate } from '@tolgee/react';
import { FormikProps } from 'formik';
import { Redirect } from 'react-router-dom';

import { TextField } from 'tg.component/common/form/fields/TextField';
import { BaseFormView } from 'tg.component/layout/BaseFormView';
import { DashboardPage } from 'tg.component/layout/DashboardPage';
import { Validation } from 'tg.constants/GlobalValidationSchema';
import { LINKS } from 'tg.constants/links';
import { components } from 'tg.service/apiSchema.generated';
import { useApiMutation, useApiQuery } from 'tg.service/http/useQueryApi';
import { usePreferredOrganization } from 'tg.globalContext/helpers';
import { OrganizationSwitch } from 'tg.component/organizationSwitch/OrganizationSwitch';
import { messageService } from 'tg.service/MessageService';

import { BaseLanguageSelect } from './components/BaseLanguageSelect';
import { CreateProjectLanguagesArrayField } from './components/CreateProjectLanguagesArrayField';

export type CreateProjectValueType = components['schemas']['CreateProjectDTO'];

export const ProjectCreateView: FunctionComponent = () => {
  const createProjectLoadable = useApiMutation({
    url: '/v2/projects',
    method: 'post',
    fetchOptions: { disableErrorNotification: true },
  });
  const { t } = useTranslate();
  const { preferredOrganization, updatePreferredOrganization } =
    usePreferredOrganization();

  const onSubmit = (values: CreateProjectValueType) => {
    values.languages = values.languages.filter((l) => !!l);
    createProjectLoadable.mutate(
      {
        content: {
          'application/json': values,
        },
      },
      {
        onSuccess() {
          updatePreferredOrganization(values.organizationId);
          messageService.success(<T keyName="project_created_message" />);
        },
      }
    );
  };

  const organizationsLoadable = useApiQuery({
    url: '/v2/organizations',
    method: 'get',
    query: {
      size: 100,
      params: {
        filterCurrentUserOwner: true,
      },
    },
  });

  const initialValues: CreateProjectValueType = {
    name: '',
    languages: [
      { tag: 'en', name: 'English', originalName: 'English', flagEmoji: '🇬🇧' },
    ],
    organizationId: preferredOrganization.id,
    baseLanguageTag: 'en',
  };

  const [cancelled, setCancelled] = useState(false);

  if (cancelled || createProjectLoadable.isSuccess) {
    return <Redirect to={LINKS.PROJECTS.build()} />;
  }

  return (
    <DashboardPage>
      <BaseFormView
        lg={6}
        md={8}
        windowTitle={t('create_project_view')}
        title={t('create_project_view')}
        initialValues={initialValues}
        loading={organizationsLoadable.isLoading}
        onSubmit={onSubmit}
        onCancel={() => setCancelled(true)}
        saveActionLoadable={createProjectLoadable}
        validationSchema={Validation.PROJECT_CREATION(t)}
        switcher={<OrganizationSwitch ownedOnly />}
      >
        {(props: FormikProps<CreateProjectValueType>) => {
          return (
            <Box mb={8}>
              <Grid container spacing={2}>
                <Grid item lg md sm xs={12}>
                  <TextField
                    variant="standard"
                    autoFocus
                    data-cy="project-name-field"
                    label={<T keyName="create_project_name_label" />}
                    name="name"
                    required={true}
                  />
                </Grid>
              </Grid>
              <Box mb={2}>
                <Typography variant="h6">
                  <T keyName="project_create_languages_title" />
                </Typography>
              </Box>
              <CreateProjectLanguagesArrayField />
              <Box mt={4}>
                <Typography variant="h6">
                  <T keyName="project_create_base_language_label" />
                </Typography>
                <BaseLanguageSelect
                  valueKey="tag"
                  name="baseLanguageTag"
                  languages={props.values.languages!}
                />
              </Box>
            </Box>
          );
        }}
      </BaseFormView>
    </DashboardPage>
  );
};
