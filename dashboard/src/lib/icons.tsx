/**
 * Icon set for the whole dashboard. Same names as @tabler/icons-react; in the Retro OS (light)
 * theme each one renders its pixel-art twin from PixelIcon instead. Import icons from here, not Tabler.
 */
import { forwardRef } from 'react';
import { useComputedColorScheme } from '@mantine/core';
import {
  IconAlertTriangle as TAlertTriangle,
  IconArrowDown as TArrowDown,
  IconArrowUp as TArrowUp,
  IconBell as TBell,
  IconBellRinging as TBellRinging,
  IconBrandGoogle as TBrandGoogle,
  IconBulb as TBulb,
  IconCalendarTime as TCalendarTime,
  IconCategory as TCategory,
  IconChartBar as TChartBar,
  IconCheck as TCheck,
  IconChecklist as TChecklist,
  IconChecks as TChecks,
  IconChevronDown as TChevronDown,
  IconChevronLeft as TChevronLeft,
  IconChevronRight as TChevronRight,
  IconCircleCheckFilled as TCircleCheckFilled,
  IconCircleDashed as TCircleDashed,
  IconCloudUpload as TCloudUpload,
  IconCrown as TCrown,
  IconDeviceMobile as TDeviceMobile,
  IconDeviceTablet as TDeviceTablet,
  IconDevices as TDevices,
  IconDownload as TDownload,
  IconEye as TEye,
  IconEyeOff as TEyeOff,
  IconFilterOff as TFilterOff,
  IconFlag as TFlag,
  IconFlask as TFlask,
  IconGripVertical as TGripVertical,
  IconHeart as THeart,
  IconHeartFilled as THeartFilled,
  IconHelp as THelp,
  IconHome as THome,
  IconInfoCircle as TInfoCircle,
  IconLayoutGrid as TLayoutGrid,
  IconList as TList,
  IconLogout as TLogout,
  IconMail as TMail,
  IconMoon as TMoon,
  IconPencil as TPencil,
  IconPhoto as TPhoto,
  IconPhotoPlus as TPhotoPlus,
  IconPhotoStar as TPhotoStar,
  IconPlus as TPlus,
  IconRefresh as TRefresh,
  IconRocket as TRocket,
  IconSearch as TSearch,
  IconSend as TSend,
  IconSettings as TSettings,
  IconShieldCheck as TShieldCheck,
  IconSparkles as TSparkles,
  IconStack2 as TStack2,
  IconStar as TStar,
  IconStarFilled as TStarFilled,
  IconStarOff as TStarOff,
  IconSun as TSun,
  IconTrash as TTrash,
  IconUpload as TUpload,
  IconUsers as TUsers,
  IconX as TX,
  type IconProps,
} from '@tabler/icons-react';
import { PixelIcon, type PixelName } from '../components/PixelIcon';

type TablerIcon = typeof TPhoto;

function swap(Modern: TablerIcon, pixel: PixelName, name: string) {
  const C = forwardRef<SVGSVGElement, IconProps>((props, ref) => {
    const retro = useComputedColorScheme('light') === 'light';
    if (!retro) return <Modern ref={ref} {...props} />;
    const { size = 24, className, style, color } = props;
    const n = Number(size);
    const px = n >= 20 && n <= 26 ? 24 : size; // whole-pixel 2x grid where it fits
    return <PixelIcon name={pixel} size={px} className={className} style={color ? { color, ...style } : style} />;
  });
  C.displayName = name;
  return C;
}

export const IconAlertTriangle = swap(TAlertTriangle, 'alert', 'IconAlertTriangle');
export const IconArrowDown = swap(TArrowDown, 'arrowDown', 'IconArrowDown');
export const IconArrowUp = swap(TArrowUp, 'arrowUp', 'IconArrowUp');
export const IconBell = swap(TBell, 'bell', 'IconBell');
export const IconBellRinging = swap(TBellRinging, 'bell', 'IconBellRinging');
export const IconBrandGoogle = swap(TBrandGoogle, 'google', 'IconBrandGoogle');
export const IconBulb = swap(TBulb, 'bulb', 'IconBulb');
export const IconCalendarTime = swap(TCalendarTime, 'calendar', 'IconCalendarTime');
export const IconCategory = swap(TCategory, 'grid', 'IconCategory');
export const IconChartBar = swap(TChartBar, 'chart', 'IconChartBar');
export const IconCheck = swap(TCheck, 'check', 'IconCheck');
export const IconChecklist = swap(TChecklist, 'checklist', 'IconChecklist');
export const IconChecks = swap(TChecks, 'check', 'IconChecks');
export const IconChevronDown = swap(TChevronDown, 'chevronDown', 'IconChevronDown');
export const IconChevronLeft = swap(TChevronLeft, 'chevronLeft', 'IconChevronLeft');
export const IconChevronRight = swap(TChevronRight, 'chevronRight', 'IconChevronRight');
export const IconCircleCheckFilled = swap(TCircleCheckFilled, 'checkbox', 'IconCircleCheckFilled');
export const IconCircleDashed = swap(TCircleDashed, 'box', 'IconCircleDashed');
export const IconCloudUpload = swap(TCloudUpload, 'upload', 'IconCloudUpload');
export const IconCrown = swap(TCrown, 'crown', 'IconCrown');
export const IconDeviceMobile = swap(TDeviceMobile, 'mobile', 'IconDeviceMobile');
export const IconDeviceTablet = swap(TDeviceTablet, 'tablet', 'IconDeviceTablet');
export const IconDevices = swap(TDevices, 'mobile', 'IconDevices');
export const IconDownload = swap(TDownload, 'download', 'IconDownload');
export const IconEye = swap(TEye, 'eye', 'IconEye');
export const IconEyeOff = swap(TEyeOff, 'eyeOff', 'IconEyeOff');
export const IconFilterOff = swap(TFilterOff, 'filterOff', 'IconFilterOff');
export const IconFlag = swap(TFlag, 'flag', 'IconFlag');
export const IconFlask = swap(TFlask, 'flask', 'IconFlask');
export const IconGripVertical = swap(TGripVertical, 'grip', 'IconGripVertical');
export const IconHeart = swap(THeart, 'heartEmpty', 'IconHeart');
export const IconHeartFilled = swap(THeartFilled, 'heart', 'IconHeartFilled');
export const IconHelp = swap(THelp, 'help', 'IconHelp');
export const IconHome = swap(THome, 'home', 'IconHome');
export const IconInfoCircle = swap(TInfoCircle, 'info', 'IconInfoCircle');
export const IconLayoutGrid = swap(TLayoutGrid, 'grid', 'IconLayoutGrid');
export const IconList = swap(TList, 'checklist', 'IconList');
export const IconLogout = swap(TLogout, 'logout', 'IconLogout');
export const IconMail = swap(TMail, 'mail', 'IconMail');
export const IconMoon = swap(TMoon, 'moon', 'IconMoon');
export const IconPencil = swap(TPencil, 'pencil', 'IconPencil');
export const IconPhoto = swap(TPhoto, 'picture', 'IconPhoto');
export const IconPhotoPlus = swap(TPhotoPlus, 'picture', 'IconPhotoPlus');
export const IconPhotoStar = swap(TPhotoStar, 'picture', 'IconPhotoStar');
export const IconPlus = swap(TPlus, 'plus', 'IconPlus');
export const IconRefresh = swap(TRefresh, 'refresh', 'IconRefresh');
export const IconRocket = swap(TRocket, 'rocket', 'IconRocket');
export const IconSearch = swap(TSearch, 'search', 'IconSearch');
export const IconSend = swap(TSend, 'send', 'IconSend');
export const IconSettings = swap(TSettings, 'gear', 'IconSettings');
export const IconShieldCheck = swap(TShieldCheck, 'shield', 'IconShieldCheck');
export const IconSparkles = swap(TSparkles, 'sparkles', 'IconSparkles');
export const IconStack2 = swap(TStack2, 'stack', 'IconStack2');
export const IconStar = swap(TStar, 'starEmpty', 'IconStar');
export const IconStarFilled = swap(TStarFilled, 'star', 'IconStarFilled');
export const IconStarOff = swap(TStarOff, 'starOff', 'IconStarOff');
export const IconSun = swap(TSun, 'sun', 'IconSun');
export const IconTrash = swap(TTrash, 'trash', 'IconTrash');
export const IconUpload = swap(TUpload, 'upload', 'IconUpload');
export const IconUsers = swap(TUsers, 'users', 'IconUsers');
export const IconX = swap(TX, 'x', 'IconX');
