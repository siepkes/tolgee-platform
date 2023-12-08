import { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';

import { AppState } from 'tg.store/index';
import { useApiMutation, useApiQuery } from 'tg.service/http/useQueryApi';
import { components } from 'tg.service/apiSchema.generated';
import { InvitationCodeService } from 'tg.service/InvitationCodeService';
import { useTolgee } from '@tolgee/react';
import { useOnUpdate } from 'tg.hooks/useOnUpdate';
import { globalActions } from 'tg.store/global/GlobalActions';

type PrivateOrganizationModel =
  components['schemas']['PrivateOrganizationModel'];
type AnnouncementDto = components['schemas']['AnnouncementDto'];

export const useInitialDataService = () => {
  const [organizationLoading, setOrganizationLoading] = useState(false);
  const tolgee = useTolgee();

  const [organization, setOrganization] = useState<
    PrivateOrganizationModel | undefined
  >(undefined);
  const security = useSelector((state: AppState) => state.global.security);
  const [announcement, setAnnouncement] = useState<AnnouncementDto | null>();
  const initialData = useApiQuery({
    url: '/v2/public/initial-data',
    method: 'get',
    options: {
      refetchOnMount: false,
      cacheTime: Infinity,
      keepPreviousData: true,
      staleTime: Infinity,
    },
  });

  useEffect(() => {
    const data = initialData.data;
    if (data) {
      // set organization data only if missing
      setOrganization((org) => (org ? org : data.preferredOrganization));
      setAnnouncement(data.announcement);
      if (data.languageTag) {
        // switch ui language, once user is signed in
        tolgee.changeLanguage(data.languageTag);
      }
      const invitationCode = InvitationCodeService.getCode();
      globalActions.updateSecurity.dispatch({
        allowPrivate:
          !data?.serverConfiguration?.authentication || Boolean(data.userInfo),
        allowRegistration:
          data.serverConfiguration.allowRegistrations ||
          Boolean(invitationCode), // if user has invitation code, registration is allowed
      });
    }
  }, [Boolean(initialData.data)]);

  useEffect(() => {
    if (initialData.data) {
      setAnnouncement(initialData.data.announcement);
    }
  }, [initialData.data]);

  const preferredOrganizationLoadable = useApiMutation({
    url: '/v2/preferred-organization',
    method: 'get',
  });

  const setPreferredOrganization = useApiMutation({
    url: '/v2/user-preferences/set-preferred-organization/{organizationId}',
    method: 'put',
  });

  const dismissAnnouncementLoadable = useApiMutation({
    url: '/v2/announcement/dismiss',
    method: 'post',
  });

  const preferredOrganization =
    organization ?? initialData.data?.preferredOrganization;

  const updatePreferredOrganization = async (organizationId: number) => {
    if (organizationId !== preferredOrganization?.id) {
      setOrganizationLoading(true);
      try {
        // set preffered organization
        await setPreferredOrganization.mutateAsync({
          path: { organizationId },
        });

        // load new preferred organization
        const data = await preferredOrganizationLoadable.mutateAsync({});
        setOrganization(data);
      } finally {
        setOrganizationLoading(false);
      }
    }
  };

  const refetchInitialData = () => {
    setOrganization(undefined);
    return initialData.refetch();
  };

  const dismissAnnouncement = () => {
    setAnnouncement(null);
    dismissAnnouncementLoadable.mutate(
      {},
      {
        onError() {
          setAnnouncement(announcement);
        },
      }
    );
  };

  useOnUpdate(() => {
    refetchInitialData();
  }, [security.jwtToken]);

  const isFetching =
    initialData.isFetching ||
    setPreferredOrganization.isLoading ||
    preferredOrganizationLoadable.isLoading ||
    dismissAnnouncementLoadable.isLoading ||
    organizationLoading;

  if (initialData.error) {
    throw initialData.error;
  }

  return {
    data: {
      ...initialData.data!,
      preferredOrganization,
      announcement,
    },
    isFetching,
    isLoading: initialData.isLoading,

    refetchInitialData,
    updatePreferredOrganization,
    dismissAnnouncement,
  };
};
